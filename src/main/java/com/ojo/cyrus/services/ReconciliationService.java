package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.ReconciliationProperties;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.ReconciliationOutcome;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.nomba.NombaClient;
import com.ojo.cyrus.nomba.dto.NombaCredentials;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import com.ojo.cyrus.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconciles a payment transaction against Nomba's requery endpoint to confirm receipt and match
 * amounts. Given a requestId (webhook delivery identifier), finds the Transaction and validates it
 * against Nomba's independent record.
 *
 * <p>Nomba/DB failures (timeouts, 5xx, connection errors) are NOT caught here — they propagate so
 * JobRunr's own retry/backoff takes over the job, the same mechanism it uses for any other failed
 * job. A clean "not found yet" result (session unrecognized or still settling) is different: it's
 * not a failure, so JobRunr's retry doesn't apply — instead this service re-schedules itself with
 * backoff, up to {@code reconciliation.max-attempts}, then gives up and flags the transaction
 * {@code MANUAL_REVIEW} for a human to look at.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {
    private final MerchantService merchantService;
    private final NombaClient nombaClient;
    private final TransactionRepository transactionRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobScheduler jobScheduler;
    private final ReconciliationProperties reconciliationProperties;

    /**
     * Reconciles a transaction against Nomba's requery endpoint using the webhook requestId.
     * Updates the transaction's matchStatus and status based on the outcome, and — for a "not
     * found yet" result — schedules its own retry rather than returning a final answer.
     *
     * @param requestId the requestId denormalized onto Transaction (same value as the originating
     *                   PaymentEvent's requestId)
     * @return the outcome: MATCHED (confirmed, amounts agree), DISCREPANCY (confirmed, field mismatch),
     *         or NOT_FOUND (not yet confirmed — a retry has been scheduled, or attempts are exhausted)
     */
    public ReconciliationOutcome reconcileByRequestId(String requestId) {
        Transaction tx = transactionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("No transaction found for requestId=" + requestId));

        if (tx.getSessionId() == null || tx.getSessionId().isBlank()) {
            log.warn("Transaction {} has no sessionId, cannot requery — flagging for manual review", tx.getId());
            markManualReview(requestId, "No Nomba sessionId recorded; cannot requery");
            return ReconciliationOutcome.NOT_FOUND;
        }

        // Nomba/DB failures deliberately propagate uncaught here — JobRunr retries the job.
        NombaCredentials creds = merchantService.getNombaCredentials(tx.getMerchant().getId());
        NombaTransactionData providerTx = nombaClient.requeryTransaction(creds, tx.getSessionId(), tx.getEnvironment());

        RequeryApplication result = applyRequeryResult(tx.getId(), providerTx);
        if (result.outcome() == ReconciliationOutcome.NOT_FOUND) {
            scheduleRetryOrGiveUp(requestId, result.attempts());
        }
        return result.outcome();
    }

    private record RequeryApplication(ReconciliationOutcome outcome, int attempts) {}

    /**
     * "Found" is inferred from a non-blank {@code transactionId} in the response, not the
     * response's own status field — live testing showed Nomba returns a generic success envelope
     * with a zeroed-out transaction for a session it doesn't recognize.
     */
    private RequeryApplication applyRequeryResult(UUID transactionId, NombaTransactionData providerTx) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            // Re-fetch inside this transaction: tx was loaded outside any transaction by the
            // caller and is detached, so mutating it directly here would not persist.
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

            // Nomba confirmed the transfer — promote to SUCCESSFUL, but only from PENDING: a
            // reversal webhook may have flipped this to REVERSED while the job was waiting, and
            // requery confirming the ORIGINAL transfer must not undo the clawback (REVERSED ->
            // SUCCESSFUL would report a clawed-back payment as successful).
            if (tx.getStatus() == TransactionStatus.PENDING) {
                tx.setStatus(TransactionStatus.SUCCESSFUL);
            }

            List<String> diffs = new ArrayList<>();
            BigInteger providerAmount = NombaCurrencyUtil.nairaToKobo(providerTx.transactionAmount());
            if (!providerAmount.equals(tx.getAmount())) {
                diffs.add("Webhook amount=" + tx.getAmount() + ", Nomba requery amount=" + providerAmount);
            }
            // Compare fees only when both sides report one — a live VA-credit requery response
            // carries no fee field at all, and treating that absence as zero would manufacture a
            // false DISCREPANCY against the webhook's real fee.
            if (tx.getFee() != null && providerTx.fee() != null && !providerTx.fee().isBlank()) {
                BigInteger providerFee = NombaCurrencyUtil.nairaToKobo(providerTx.fee());
                if (!providerFee.equals(tx.getFee())) {
                    diffs.add("Webhook fee=" + tx.getFee() + ", Nomba requery fee=" + providerFee);
                }
            }
            if (providerTx.currency() != null && !providerTx.currency().equalsIgnoreCase(tx.getCurrency())) {
                diffs.add("Webhook currency=" + tx.getCurrency() + ", Nomba requery currency=" + providerTx.currency());
            }

            if (diffs.isEmpty()) {
                tx.setMatchStatus(MatchStatus.MATCHED);
                tx.setMatchStatusDetails(null);
                log.info("Transaction {} reconciled: MATCHED", tx.getId());
                return new RequeryApplication(ReconciliationOutcome.MATCHED, tx.getReconciliationAttempts());
            }

            tx.setMatchStatus(MatchStatus.DISCREPANCY);
            tx.setMatchStatusDetails(String.join("; ", diffs));
            log.warn("Transaction {} reconciled: DISCREPANCY - {}", tx.getId(), String.join("; ", diffs));
            return new RequeryApplication(ReconciliationOutcome.DISCREPANCY, tx.getReconciliationAttempts());
        });
    }

    private void scheduleRetryOrGiveUp(String requestId, int attempts) {
        if (attempts >= reconciliationProperties.maxAttempts()) {
            log.warn("Giving up on requestId={} after {} attempts — flagging for manual review", requestId, attempts);
            markManualReview(requestId, "Not confirmed by Nomba after " + attempts + " attempts");
            return;
        }
        Instant runAt = Instant.now().plusSeconds(reconciliationProperties.delaySeconds());
        jobScheduler.schedule(UUID.randomUUID(), runAt, () -> reconcileByRequestId(requestId));
    }

    private void markManualReview(String requestId, String details) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                transactionRepository.findByRequestId(requestId).ifPresent(tx -> {
                    tx.setMatchStatus(MatchStatus.MANUAL_REVIEW);
                    tx.setMatchStatusDetails(details);
                    tx.setLastReconciledAt(Instant.now());
                }));
    }
}
