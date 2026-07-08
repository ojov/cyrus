package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // Dedup key for an inbound payment — Nomba's transaction id (single provider now, so no
    // composite with provider).
    boolean existsByProviderTransactionId(String providerTransactionId);

    Optional<Transaction> findByProviderTransactionId(String providerTransactionId);

    Optional<Transaction> findByReference(String reference);

    // Fetch by webhook requestId for direct reconciliation lookup.
    Optional<Transaction> findByRequestId(String requestId);

    // Fallback match for a reversal whose transactionId doesn't line up with the original —
    // sessionId is the other stable identifier Nomba carries across the transfer's lifecycle.
    Optional<Transaction> findBySessionId(String sessionId);

    // Reconciliation candidates: unmatched transactions past the grace period (skip anything too
    // recent — the webhook may just not have caught up yet).
    List<Transaction> findByMatchStatusAndReceivedAtBefore(MatchStatus matchStatus, Instant cutoff);

    // Sweep fallback for transactions that missed (or failed) their immediate post-ingestion
    // reconcileAsync call — still PENDING, not already flagged for manual review (e.g. a null
    // sessionId, which never increments reconciliationAttempts and so wouldn't otherwise age out of
    // the attempt cap below), under the attempt cap, and not retried too recently.
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.status = com.ojo.cyrus.enums.TransactionStatus.PENDING
            AND t.matchStatus <> com.ojo.cyrus.enums.MatchStatus.MANUAL_REVIEW
            AND t.reconciliationAttempts < :maxAttempts
            AND (t.lastReconciledAt IS NULL OR t.lastReconciledAt < :cutoff)
            """)
    List<Transaction> findDueForReconciliation(@Param("maxAttempts") int maxAttempts,
                                                @Param("cutoff") Instant cutoff);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.merchant.id = :merchantId AND t.status = :status
            """)
    BigInteger sumAmountByMerchantAndStatus(@Param("merchantId") UUID merchantId,
                                            @Param("status") TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.customer.id = :customerId AND t.status = :status
            """)
    BigInteger sumAmountByCustomerAndStatus(@Param("customerId") UUID customerId,
                                            @Param("status") TransactionStatus status);

    // Statement summary aggregates — always over the customer's full history, independent of
    // whatever from/to/matchStatus filter is applied to the paginated row list above.
    long countByCustomerId(UUID customerId);

    long countByCustomerIdAndStatus(UUID customerId, TransactionStatus status);

    long countByCustomerIdAndMatchStatus(UUID customerId, MatchStatus matchStatus);

    @Query("SELECT MAX(t.receivedAt) FROM Transaction t WHERE t.customer.id = :customerId")
    Instant findLastReceivedAtByCustomerId(@Param("customerId") UUID customerId);
}
