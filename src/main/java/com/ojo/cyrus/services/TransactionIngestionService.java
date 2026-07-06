package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.ReconciliationProperties;
import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.MerchantWebhookEventType;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.nomba.NombaWebhookAdapter;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.ojo.cyrus.utils.Mapper.buildTransaction;

/**
 * Records a Nomba payment event and attributes it to a customer's virtual account. Idempotent and
 * atomic: the raw event and the derived transaction are persisted in one transaction.
 *
 * <p>A webhook is a notification, not proof — a genuine VA credit is recorded as
 * {@link TransactionStatus#PENDING} with {@code matchStatus=UNMATCHED}, never {@code SUCCESSFUL},
 * regardless of what the event claims. Only {@link ReconciliationService}, via Nomba's own
 * requery endpoint, promotes it to {@code SUCCESSFUL}.
 *
 * <p>Failure handling is tuned for Nomba's retry policy (exponential backoff, 5 retries):
 * <ul>
 *   <li>Duplicate delivery (by requestId) -> recorded/ignored, returns 2xx to stop retries.</li>
 *   <li>Duplicate transaction (by providerTransactionId) -> recorded/ignored, returns 2xx.</li>
 *   <li>Orphan payment (no matching virtual account) -> recorded as {@code IGNORED} for reconciliation;
 *       returns 2xx because retrying will not resolve the missing account.</li>
 *   <li>Transient failures (e.g. DB connection) propagate -> non-2xx -> Nomba retries.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {
    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentEventService paymentEventService;
    private final NombaWebhookAdapter nombaAdapter;
    private final ReconciliationService reconciliationService;
    private final JobScheduler jobScheduler;
    private final ReconciliationProperties reconciliationProperties;
    private final MerchantWebhookService merchantWebhookService;

    /**
     * @return the created {@link Transaction} only when this event is a genuine VA credit —
     *         {@link Optional#empty()} for reversals, failures, non-credit events, orphans, and
     *         duplicates, none of which have a transaction to reconcile.
     */
    @Transactional
    public Optional<Transaction> ingest(NormalizedPaymentEvent event, String rawPayload) {
        // 1. Event-level idempotency — find or create atomically.
        // Two concurrent deliveries of the same requestId can both miss the lookup below,
        // so we catch the unique-constraint violation on insert and re-fetch the winner's row.
        PaymentEvent paymentEvent;
        try {
            paymentEvent = paymentEventService.findByRequestId(event.getRequestId()).orElseGet(() ->
                paymentEventService.recordEvent(event.getRequestId(), event.getProvider(),
                    event.getEventType(), rawPayload));
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent insert won for requestId={}, re-fetching", event.getRequestId());
            paymentEvent = paymentEventService.findByRequestId(event.getRequestId())
                .orElseThrow(() -> new IllegalStateException(
                    "PaymentEvent not found after concurrent insert for requestId=" + event.getRequestId()));
        }

        // 2. Reversal — a previously successful payment clawed back. This must flip the original
        //    transaction's status, not just get filed away as a generic non-credit event (that would
        //    leave the original sitting as SUCCESSFUL forever, wrong for reconciliation).
        if (event.isReversal()) {
            handleReversal(event, paymentEvent);
            return Optional.empty();
        }

        // 3. Failure — a payment attempt that never credited us. Distinguished from IGNORED (which
        //    means "not actionable") so it stays visible in reconciliation/exception views.
        if (event.isFailure()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.FAILED,
                    "Payment failed: " + event.getEventType());
            log.info("Recording failed Nomba payment requestId={} tx={}",
                    event.getRequestId(), event.getProviderTransactionId());
            return Optional.empty();
        }

        // 4. Relevance gate — only successful VA credits become transactions. Everything else we
        //    don't act on (e.g. POS purchases, payouts) is recorded for audit but never minted as one.
        if (!event.isVirtualAccountCredit()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "Non-credit event: " + event.getEventType());
            log.info("Ignoring non-credit Nomba event requestId={} type={}",
                    event.getRequestId(), event.getEventType());
            return Optional.empty();
        }

        // 5. Transaction-level idempotency — same underlying transfer seen before.
        if (transactionRepository.existsByProviderAndProviderTransactionId(event.getProvider(), event.getProviderTransactionId())) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.PROCESSED_DUPLICATE,
                    "Duplicate transaction " + event.getProviderTransactionId());
            return Optional.empty();
        }

        // 6. Attribute to a virtual account. Unknown VA = orphan payment: record, don't retry.
        Optional<VirtualAccount> vaOpt =
                virtualAccountRepository.findByAccountNumber(event.getVirtualAccountNumber());
        if (vaOpt.isEmpty()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "No virtual account for " + event.getVirtualAccountNumber());
            log.warn("Orphan Nomba payment: no VA for accountNumber={} (tx {})",
                    event.getVirtualAccountNumber(), event.getProviderTransactionId());
            return Optional.empty();
        }

        // 7. Record the transaction, attributed to the customer.
        VirtualAccount va = vaOpt.get();
        Customer customer = va.getCustomer();
        NormalizedPaymentEvent.Payer payer = event.getPayer();

        Transaction tx = transactionRepository.save(
                buildTransaction(event, rawPayload, customer, va, payer, paymentEvent));
        paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.PROCESSED, null);

        scheduleReconciliation(tx);

        log.info("Ingested Nomba payment tx {} for customer {} on VA {}",
                event.getProviderTransactionId(), customer.getReference(), va.getAccountNumber());
        return Optional.of(tx);
    }

    /**
     * Schedules the delayed reconciliation requery for a freshly-minted transaction. Registered as
     * an afterCommit hook so the JobRunr job only exists once the transaction row is durably
     * committed (a job firing against an uncommitted/rolled-back row would find nothing). Living
     * here — not in the webhook layer — means every ingestion path (webhook, admin replay, future
     * backfill) gets reconciliation automatically.
     */
    private void scheduleReconciliation(Transaction tx) {
        UUID transactionId = tx.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Instant runAt = Instant.now().plusSeconds(reconciliationProperties.delaySeconds());
                jobScheduler.schedule(transactionId, runAt, () -> reconciliationService.reconcileTransactionById(transactionId));
            }
        });
    }

    /**
     * Flips the original transaction to REVERSED. We don't yet have a confirmed sample of Nomba's
     * {@code payment_reversal} payload, so the match is defensive: try the same providerTransactionId
     * as the original transfer first, then fall back to sessionId (the other stable identifier Nomba
     * carries across a transfer's lifecycle). If neither matches, record — don't guess, don't throw.
     */
    private void handleReversal(NormalizedPaymentEvent event, PaymentEvent paymentEvent) {
        Optional<Transaction> original = transactionRepository
                .findByProviderAndProviderTransactionId(event.getProvider(), event.getProviderTransactionId());

        if (original.isEmpty() && event.getSessionId() != null && !event.getSessionId().isBlank()) {
            original = transactionRepository.findByProviderAndSessionId(event.getProvider(), event.getSessionId());
        }

        if (original.isEmpty()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "Reversal for unknown transaction " + event.getProviderTransactionId());
            log.warn("Unmatched Nomba reversal: no transaction for tx={} session={}",
                    event.getProviderTransactionId(), event.getSessionId());
            return;
        }

        Transaction tx = original.get();
        tx.setStatus(TransactionStatus.REVERSED);
        paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.PROCESSED, null);
        // Notify the merchant of the clawback — outbox row written in this same @Transactional ingest.
        merchantWebhookService.recordAndScheduleDispatch(tx, MerchantWebhookEventType.PAYMENT_REVERSED);
        log.info("Reversed Nomba payment tx {} (providerTransactionId={})", tx.getId(), tx.getProviderTransactionId());
    }

    /**
     * Replays a specific event by triggering its ingestion again.
     */
    @Transactional
    public void replayEvent(UUID id) {
        PaymentEvent event = paymentEventService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentEvent not found: " + id));

        log.info("Replaying payment event: {}", id);

        if (event.getProvider() == Provider.NOMBA) {
            NormalizedPaymentEvent cyrusEvent = nombaAdapter.toCyrusEvent(event.getPayload());
            this.ingest(cyrusEvent, event.getPayload());
        } else {
            throw new UnsupportedOperationException("Replay not implemented for provider: " + event.getProvider());
        }
    }
}
