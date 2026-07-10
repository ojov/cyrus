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
import com.ojo.cyrus.nomba.clients.NombaTransactionClient;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.utils.FeeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
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
 * <p>Reconciliation is no longer JobRunr-scheduled: {@link #reconcileAsync(UUID)} runs it immediately
 * (off the ingesting request thread) right after a transaction is recorded, and
 * {@link #sweepPendingReconciliations()} is a periodic fallback that catches anything still PENDING —
 * a Nomba requery that wasn't confirmed yet, or an async attempt that itself failed. JobRunr is kept
 * only for outbound merchant webhook delivery, which genuinely needs durable scheduled retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    private final NombaTransactionClient nombaTransactionClient;
    private final TransactionRepository transactionRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationProperties reconciliationProperties;
    private final MerchantWebhookService merchantWebhookService;
    private final LedgerService ledgerService;
    private final FeeProperties feeProperties;

    /**
     * Fire-and-forget entry point called right after a transaction is committed. Exceptions are
     * caught and logged rather than propagated (there is no caller left to handle them on an async
     * thread) — an unconfirmed or failed attempt here is simply picked up by the next sweep.
     */
    @Async
    public void reconcileAsync(UUID transactionId) {
        try {
            reconcileTransactionById(transactionId);
        } catch (Exception e) {
            log.error("Async reconciliation failed for transaction {} — will retry on the next sweep",
                    transactionId, e);
        }
    }

    /**
     * Periodic fallback for transactions still PENDING after their immediate {@link #reconcileAsync}
     * attempt — either Nomba hadn't confirmed the transfer yet, or that attempt itself failed.
     * {@link com.ojo.cyrus.repositories.TransactionRepository#findDueForReconciliation} already
     * excludes anything already flagged {@code MANUAL_REVIEW} or past the attempt cap.
     */
    @Scheduled(fixedDelayString = "#{${app.reconciliation.delay-seconds} * 1000}")
    public void sweepPendingReconciliations() {
        Instant cutoff = Instant.now().minusSeconds(reconciliationProperties.delaySeconds());
        List<Transaction> due = transactionRepository.findDueForReconciliation(
                reconciliationProperties.maxAttempts(), cutoff);
        if (due.isEmpty()) {
            return;
        }
        log.info("Reconciliation sweep: {} transaction(s) due", due.size());
        for (Transaction tx : due) {
            try {
                reconcileTransactionById(tx.getId());
            } catch (Exception e) {
                log.error("Sweep reconciliation failed for transaction {}", tx.getId(), e);
            }
        }
    }

    public ReconciliationOutcome reconcileTransactionById(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("No transaction found for transactionId=" + transactionId));

        if (tx.getSessionId() == null || tx.getSessionId().isBlank()) {
            log.warn("Transaction {} has no sessionId, cannot requery — flagging for manual review", tx.getId());
            markManualReview(tx.getId(), "No sessionId captured at ingestion — cannot requery Nomba");
            return ReconciliationOutcome.NOT_FOUND;
        }

        // Nomba/DB failures deliberately propagate uncaught — the caller (reconcileAsync or the
        // sweep) catches and logs; reconciliationAttempts is left unchanged so the sweep retries it.
        NombaTransactionData providerTx = nombaTransactionClient.requeryTransaction(tx.getSessionId());

        RequeryApplication result = applyRequeryResult(tx.getId(), providerTx);
        if (result.outcome() == ReconciliationOutcome.NOT_FOUND
                && result.attempts() >= reconciliationProperties.maxAttempts()) {
            log.warn("Giving up on transaction={} after {} attempts — flagging for manual review",
                    transactionId, result.attempts());
            markManualReview(transactionId, "Not confirmed by Nomba after " + result.attempts() + " attempts");
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

            BigInteger providerAmount = NombaCurrencyUtil.nairaToKobo(providerTx.transactionAmount());
            List<String> diffs = new ArrayList<>();
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
            // CRITICAL: On DISCREPANCY, we credit the Nomba-confirmed amount (providerAmount), not the
            // webhook amount. The webhook is a notification; Nomba's requery is the source of truth.
            // The transaction amount is updated to the confirmed amount so the ledger reflects reality.
            //
            // The gross amount is credited, then Nomba's own fee (PROVIDER_FEE) and Cyrus's margin
            // on top of it (PLATFORM_FEE) are debited back out, so the wallet's net change is
            // gross - merchantFee. The merchant fee is computed independently as a percentage of
            // the confirmed gross (with min/max caps — see FeeProperties) rather than a markup on Nomba's fee.
            if (promoted) {
                // Use the Nomba-confirmed amount for crediting. On DISCREPANCY, update the transaction
                // amount to reflect what Nomba actually confirmed — this is the source of truth.
                BigInteger creditAmount = providerAmount;
                if (!providerAmount.equals(tx.getAmount())) {
                    log.warn("Transaction {} amount corrected: webhook={} → confirmed={}",
                            tx.getId(), tx.getAmount(), providerAmount);
                    tx.setAmount(providerAmount);
                }

                ledgerService.credit(tx.getMerchant(), creditAmount, tx,
                        LedgerEntryType.MERCHANT_WALLET_CREDIT, "Payment " + tx.getReference());

                if (confirmedFee != null && confirmedFee.signum() > 0) {
                    BigInteger merchantFee = FeeCalculator.computeInflowMerchantFee(creditAmount, feeProperties);
                    BigInteger cyrusMargin = merchantFee.subtract(confirmedFee).max(BigInteger.ZERO);
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
