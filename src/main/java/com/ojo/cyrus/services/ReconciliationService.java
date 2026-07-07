package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.FeeProperties;
import com.ojo.cyrus.config.properties.ReconciliationProperties;
import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantWebhookEventType;
import com.ojo.cyrus.enums.ReconciliationOutcome;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.dto.RequeryApplication;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.nomba.NombaTransactionClient;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.utils.FeeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles a payment transaction against Nomba's requery endpoint (the source of truth) to confirm
 * receipt and match amounts. On the transition PENDING → SUCCESSFUL it credits the merchant's wallet
 * with a double-entry {@link LedgerEntryType#MERCHANT_WALLET_CREDIT} posting, atomic with the status
 * change.
 *
 * <p>Nomba/DB failures propagate (JobRunr retries the job). A clean "not found yet" result is not a
 * failure: this service re-schedules itself with backoff up to {@code reconciliation.max-attempts},
 * then flags the transaction {@code MANUAL_REVIEW}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    private final NombaTransactionClient nombaTransactionClient;
    private final TransactionRepository transactionRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobScheduler jobScheduler;
    private final ReconciliationProperties reconciliationProperties;
    private final MerchantWebhookService merchantWebhookService;
    private final LedgerService ledgerService;
    private final FeeProperties feeProperties;

    public ReconciliationOutcome reconcileTransactionById(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("No transaction found for transactionId=" + transactionId));

        if (tx.getSessionId() == null || tx.getSessionId().isBlank()) {
            log.warn("Transaction {} has no sessionId, cannot requery — flagging for manual review", tx.getId());
            return ReconciliationOutcome.NOT_FOUND;
        }

        // Nomba/DB failures deliberately propagate uncaught here — JobRunr retries the job.
        NombaTransactionData providerTx = nombaTransactionClient.requeryTransaction(tx.getSessionId());

        RequeryApplication result = applyRequeryResult(tx.getId(), providerTx);
        if (result.outcome() == ReconciliationOutcome.NOT_FOUND) {
            scheduleRetryOrGiveUp(tx.getId(), result.attempts());
        }
        return result.outcome();
    }

    /**
     * "Found" is inferred from a non-blank {@code transactionId} in the response, not the response's
     * own status field — live testing showed Nomba returns a generic success envelope with a zeroed
     * transaction for a session it doesn't recognize.
     */
    private RequeryApplication applyRequeryResult(UUID transactionId, NombaTransactionData providerTx) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Transaction tx = transactionRepository.findById(transactionId).orElseThrow();
            tx.setLastReconciledAt(Instant.now());

            boolean found = providerTx.transactionId() != null && !providerTx.transactionId().isBlank();
            if (!found) {
                int attempts = tx.getReconciliationAttempts() + 1;
                tx.setReconciliationAttempts(attempts);
                log.debug("Transaction {} not yet confirmed by Nomba (session={}, attempt={})",
                        tx.getId(), tx.getSessionId(), attempts);
                return new RequeryApplication(ReconciliationOutcome.NOT_FOUND, attempts);
            }

            // Nomba confirmed the transfer — promote to SUCCESSFUL, but only from PENDING: a reversal
            // webhook may have flipped this to REVERSED while the job waited, and confirming the
            // ORIGINAL transfer must not undo the clawback.
            boolean promoted = tx.getStatus() == TransactionStatus.PENDING;
            if (promoted) {
                tx.setStatus(TransactionStatus.SUCCESSFUL);
            }

            List<String> diffs = new ArrayList<>();
            BigInteger providerAmount = NombaCurrencyUtil.nairaToKobo(providerTx.transactionAmount());
            if (!providerAmount.equals(tx.getAmount())) {
                diffs.add("Webhook amount=" + tx.getAmount() + ", Nomba requery amount=" + providerAmount);
            }

            // The requery response carries no fee field at all (verified against a real live
            // response — see NombaTransactionData) — the webhook's transaction.fee, captured into
            // tx.fee at ingestion, is the only fee Nomba ever reports. Once requery confirms the
            // transaction is real, that webhook fee is trusted for the platform-fee calculation below.
            BigInteger confirmedFee = tx.getFee();

            String txCurrency = tx.getCurrency() != null ? tx.getCurrency().name() : null;
            if (providerTx.currency() != null && txCurrency != null && !providerTx.currency().equalsIgnoreCase(txCurrency)) {
                diffs.add("Webhook currency=" + txCurrency + ", Nomba requery currency=" + providerTx.currency());
            }

            ReconciliationOutcome outcome;
            if (diffs.isEmpty()) {
                tx.setMatchStatus(MatchStatus.MATCHED);
                tx.setMatchStatusDetails(null);
                log.info("Transaction {} reconciled: MATCHED", tx.getId());
                outcome = ReconciliationOutcome.MATCHED;
            } else {
                tx.setMatchStatus(MatchStatus.DISCREPANCY);
                tx.setMatchStatusDetails(String.join("; ", diffs));
                log.warn("Transaction {} reconciled: DISCREPANCY - {}", tx.getId(), String.join("; ", diffs));
                outcome = ReconciliationOutcome.DISCREPANCY;
            }

            // On confirmation, credit the merchant wallet (once, on the PENDING → SUCCESSFUL edge) and
            // notify the merchant. MATCHED and DISCREPANCY both settle the money (a DISCREPANCY is a
            // reconciliation concern carried on matchStatus); both are atomic with the status change.
            //
            // The gross amount is credited, then Nomba's own fee (PROVIDER_FEE) and Cyrus's markup
            // on top of it (PLATFORM_FEE — see FeeProperties) are debited back out, so the wallet's
            // net change is amount - totalFee. This is the actual revenue model: Cyrus marks up
            // Nomba's confirmed fee by `app.fees.markup-multiplier` and keeps the difference.
            if (promoted) {
                ledgerService.credit(tx.getMerchant(), tx.getAmount(), tx,
                        LedgerEntryType.MERCHANT_WALLET_CREDIT, "Payment " + tx.getReference());

                if (confirmedFee != null && confirmedFee.signum() > 0) {
                    BigInteger totalFee = FeeCalculator.totalPlatformFee(confirmedFee, feeProperties.markupMultiplier());
                    BigInteger cyrusMargin = totalFee.subtract(confirmedFee);
                    tx.setPlatformFeeKobo(cyrusMargin);

                    ledgerService.debit(tx.getMerchant(), confirmedFee, tx,
                            LedgerEntryType.PROVIDER_FEE, "Nomba fee for " + tx.getReference());
                    if (cyrusMargin.signum() > 0) {
                        ledgerService.debit(tx.getMerchant(), cyrusMargin, tx,
                                LedgerEntryType.PLATFORM_FEE, "Cyrus platform fee for " + tx.getReference());
                    }
                } else {
                    log.warn("Transaction {} confirmed with no fee reported by the webhook — " +
                            "crediting full gross with no platform fee applied", tx.getId());
                }

                merchantWebhookService.recordAndScheduleDispatch(tx, MerchantWebhookEventType.PAYMENT_SUCCEEDED);
            }

            return new RequeryApplication(outcome, tx.getReconciliationAttempts());
        });
    }

    private void scheduleRetryOrGiveUp(UUID transactionId, int attempts) {
        if (attempts >= reconciliationProperties.maxAttempts()) {
            log.warn("Giving up on transaction={} after {} attempts — flagging for manual review", transactionId, attempts);
            markManualReview(transactionId, "Not confirmed by Nomba after " + attempts + " attempts");
            return;
        }
        Instant runAt = Instant.now().plusSeconds(reconciliationProperties.delaySeconds());
        UUID retryJobId = UUID.nameUUIDFromBytes(
                ("reconcile:" + transactionId).getBytes(StandardCharsets.UTF_8));
        jobScheduler.schedule(retryJobId, runAt, () -> reconcileTransactionById(transactionId));
    }

    private void markManualReview(UUID transactionId, String details) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                transactionRepository.findById(transactionId).ifPresent(tx -> {
                    tx.setMatchStatus(MatchStatus.MANUAL_REVIEW);
                    tx.setMatchStatusDetails(details);
                    tx.setLastReconciledAt(Instant.now());
                    merchantWebhookService.recordAndScheduleDispatch(tx, MerchantWebhookEventType.PAYMENT_FLAGGED);
                }));
    }
}
