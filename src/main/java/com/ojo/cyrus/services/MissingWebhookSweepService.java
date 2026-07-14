package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.MissingWebhookSweepProperties;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.nomba.clients.NombaTransactionClient;
import com.ojo.cyrus.nomba.dto.NombaSubAccountTransactionPage;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.NombaPaymentEventRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.utils.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catches a payment whose webhook was never delivered at all — the "known gap" AGENTS.md previously
 * documented as impossible to close (Nomba's API was believed to have no transaction-listing
 * endpoint). It does: {@code POST /v1/transactions/accounts/{subAccountId}}
 * ({@link com.ojo.cyrus.nomba.NombaApiUri#SUBACCOUNT_TRANSACTIONS_FILTER}) lists everything on the
 * sub-account in a trailing window; a VA credit present there with no matching local
 * {@link com.ojo.cyrus.models.entities.Transaction} is a payment whose webhook was lost.
 *
 * <p><strong>The feed is bidirectional</strong> (verified against a real response): inbound VA
 * credits — a customer payment — carry {@code type="vact_transfer"}, {@code entryType="CREDIT"} and a
 * {@code virtualAccountReference} (the VA's accountRef = the customer's externalCustomerId), plus a
 * {@code recipientAccountNumber} (the VA NUBAN) and {@code sessionId}. Outbound payouts carry
 * {@code type="transfer"}, {@code entryType="DEBIT"} and a {@code merchantTxRef}, and are tracked
 * separately via {@code Payout} + its own requery sweep — so only VA credits are considered here.
 *
 * <p><strong>Matching is by {@code sessionId} as well as the provider id.</strong> The list item's
 * {@code id} ({@code "API-VACT_TRA-..."}) is not confirmed to equal the webhook's {@code transactionId}
 * (which is what {@code Transaction.providerTransactionId} stores), so matching on {@code id} alone
 * would risk flagging already-ingested payments as gaps. {@code sessionId} is Nomba's reconciliation
 * key, always captured on the {@code Transaction}, and is identical across the webhook, requery and
 * list endpoints — so it's the reliable dedup key.
 *
 * <p><strong>Recovery feeds the normal ingestion pipeline.</strong> A gap is mapped into a synthetic
 * {@link NormalizedPaymentEvent} — the same provider-agnostic shape the webhook adapter produces —
 * and handed to {@link TransactionIngestionService#ingest}, exactly as if the missing webhook had
 * finally arrived. That reuses every guarantee of the webhook path: event idempotency (keyed on the
 * synthetic {@code "sweep:<id>"} requestId), VA resolution, the inactive-VA→orphan gate, transaction
 * dedup, and — critically — reconciliation, which independently requeries Nomba by {@code sessionId}
 * to confirm the amount before any wallet credit. So a swept payment still settles only on Nomba's
 * own confirmation, never on the list item alone. If the real webhook later arrives, {@code ingest}'s
 * {@code providerTransactionId}/{@code sessionId} idempotency makes it a safe no-op (no double credit).
 *
 * <p>{@link MissingWebhookSweepProperties#dryRun()} defaults true: nothing is ingested, every detected
 * gap is only logged (with the mapped kobo amount) so the field mapping can be sanity-checked against
 * real traffic before this is trusted to move money.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MissingWebhookSweepService {

    private static final int PAGE_LIMIT = 100;

    private final NombaTransactionClient nombaTransactionClient;
    private final TransactionRepository transactionRepository;
    private final NombaPaymentEventRepository paymentEventRepository;
    private final TransactionIngestionService ingestionService;
    private final MissingWebhookSweepProperties props;

    @Scheduled(fixedDelayString = "#{${app.missing-webhook-sweep.delay-seconds} * 1000}")
    public void sweepMissingWebhooks() {
        Instant to = Instant.now();
        Instant from = to.minusSeconds(props.lookbackHours() * 3600);

        int scanned = 0;
        int gaps = 0;
        boolean sampleLogged = false;
        String cursor = null;
        do {
            NombaSubAccountTransactionPage page;
            try {
                page = nombaTransactionClient.listSubAccountTransactions(from, to, PAGE_LIMIT, cursor);
            } catch (Exception e) {
                log.error("Missing-webhook sweep: failed to list sub-account transactions (window {} -> {})",
                        from, to, e);
                return;
            }
            for (JsonNode item : page.results()) {
                scanned++;
                if (!sampleLogged && props.logSampleItem()) {
                    log.info("Missing-webhook sweep [SCHEMA SAMPLE] first item scanned this run "
                            + "(match status not yet checked) — raw={}", item.toString());
                    sampleLogged = true;
                }
                if (processItem(item)) {
                    gaps++;
                }
            }
            cursor = page.cursor();
        } while (cursor != null && !cursor.isBlank());

        if (!sampleLogged && props.logSampleItem()) {
            log.info("Missing-webhook sweep [SCHEMA SAMPLE] 0 items in window {} -> {} — nothing to sample", from, to);
        }

        if (gaps > 0) {
            log.warn("Missing-webhook sweep: {} gap(s) out of {} scanned (window {} -> {}, dryRun={})",
                    gaps, scanned, from, to, props.dryRun());
        } else {
            log.debug("Missing-webhook sweep: 0 gaps out of {} scanned (window {} -> {})", scanned, from, to);
        }
    }

    /** @return true if this item was a genuine, newly-detected gap (a VA credit with no local record). */
    private boolean processItem(JsonNode item) {
        String id = text(item, "id");
        if (id == null || id.isBlank()) {
            log.warn("Missing-webhook sweep: sub-account transaction with no id — {}", item);
            return false;
        }

        // Only an inbound credit to one of our virtual accounts is a payment that should have arrived
        // as a payment_success webhook. Outbound payouts (entryType DEBIT, no virtualAccountReference)
        // are tracked via Payout + its own requery sweep — skip them so they're never mistaken for a
        // missing payment.
        boolean isVaCredit = "CREDIT".equalsIgnoreCase(text(item, "entryType"))
                && text(item, "virtualAccountReference") != null;
        if (!isVaCredit) {
            return false;
        }

        String sessionId = text(item, "sessionId");

        // Already recorded? Match on the provider id first, then the reliable sessionId key (see the
        // class javadoc — the list `id` may not equal the webhook's transactionId). The "sweep:<id>"
        // requestId check also catches a prior sweep that already ingested this (whatever outcome
        // ingest reached — recovered Transaction, or orphan for an unknown/inactive VA).
        if (transactionRepository.existsByProviderTransactionId(id)
                || (sessionId != null && !sessionId.isBlank() && transactionRepository.existsBySessionId(sessionId))
                || paymentEventRepository.existsByRequestId("sweep:" + id)) {
            return false;
        }

        // Genuine gap: a VA credit Nomba confirms, with no local record — the webhook was lost.
        if (props.dryRun()) {
            log.warn("Missing-webhook sweep [DRY RUN] gap id={} sessionId={} customerRef={} vaNumber={} "
                            + "amountKobo={} feeKobo={} sender={} timeCreated={}",
                    id, sessionId, text(item, "virtualAccountReference"), text(item, "recipientAccountNumber"),
                    toKobo(item.path("amount")), toKobo(item.path("fixedCharge")),
                    text(item, "senderName"), text(item, "timeCreated"));
            return true;
        }

        // Live: replay the lost webhook through the normal ingestion pipeline. Caught per-item so one
        // bad row can't abort the sweep; nothing is committed on failure, so the next sweep retries it.
        try {
            ingestionService.ingest(toNormalizedEvent(item), item.toString());
            log.warn("Missing-webhook sweep: RECOVERED gap id={} sessionId={} customerRef={} amountKobo={} "
                            + "— fed into ingestion pipeline (reconciliation will confirm before crediting)",
                    id, sessionId, text(item, "virtualAccountReference"), toKobo(item.path("amount")));
        } catch (Exception e) {
            log.error("Missing-webhook sweep: failed to recover gap id={} — will retry next sweep", id, e);
        }
        return true;
    }

    /**
     * Maps a sub-account list item into the same provider-agnostic {@link NormalizedPaymentEvent} the
     * webhook adapter produces for a {@code payment_success} VA credit, so the ingestion pipeline can't
     * tell it apart from a real (if late) webhook. {@code requestId} is synthesized as
     * {@code "sweep:<id>"} for event-level idempotency; {@code sessionId} carries through so
     * reconciliation can requery Nomba to independently confirm the amount.
     */
    private NormalizedPaymentEvent toNormalizedEvent(JsonNode item) {
        String currency = text(item, "currency");
        return NormalizedPaymentEvent.builder()
                .eventType("payment_success")
                .requestId("sweep:" + text(item, "id"))
                .providerTransactionId(text(item, "id"))
                .sessionId(text(item, "sessionId"))
                .virtualAccountNumber(text(item, "recipientAccountNumber"))
                .amount(toKobo(item.path("amount")))
                .fee(toKobo(item.path("fixedCharge")))
                .currency(currency != null ? currency : "NGN")
                .eventTime(parseTime(text(item, "timeCreated")))
                .payer(NormalizedPaymentEvent.Payer.builder()
                        .name(text(item, "senderName"))
                        .accountNumber(text(item, "ktaSenderAccountNumber"))
                        .bankCode(text(item, "bankCode"))
                        .bankName(text(item, "bankName"))
                        .build())
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static Instant parseTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /**
     * Converts the list item's {@code amount}/{@code fixedCharge} (a quoted naira decimal string, e.g.
     * {@code "100.0"} — verified against a real response, same convention as the webhook and requery
     * endpoints) to canonical scale-4 kobo via {@link NombaCurrencyUtil#nairaToKobo}.
     */
    private static BigDecimal toKobo(JsonNode amountNode) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return MoneyUtil.ZERO_KOBO;
        }
        String nairaStr = amountNode.isNumber() ? amountNode.decimalValue().toPlainString() : amountNode.asString();
        return NombaCurrencyUtil.nairaToKobo(nairaStr);
    }
}
