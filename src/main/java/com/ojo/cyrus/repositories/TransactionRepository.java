package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    // Dedup key for an inbound payment — Nomba's transaction id (single provider now, so no
    // composite with provider).
    boolean existsByProviderTransactionId(String providerTransactionId);

    // Secondary dedup: same Nomba session id (aligns the same underlying transfer). Guards against
    // duplicate webhooks that carry different providerTransactionIds but the same sessionId.
    boolean existsBySessionId(String sessionId);

    Optional<Transaction> findByProviderTransactionId(String providerTransactionId);

    Optional<Transaction> findByReference(String reference);

    // Ownership-checked lookup for the developer-facing Transactions API.
    Optional<Transaction> findByReferenceAndMerchantId(String reference, UUID merchantId);

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
            WHERE t.type = com.ojo.cyrus.enums.TransactionType.CUSTOMER_PAYMENT
            AND t.status = com.ojo.cyrus.enums.TransactionStatus.PENDING
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
    BigDecimal sumAmountByMerchantAndStatus(@Param("merchantId") UUID merchantId,
                                            @Param("status") TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.customer.id = :customerId AND t.status = :status
            """)
    BigDecimal sumAmountByCustomerAndStatus(@Param("customerId") UUID customerId,
                                            @Param("status") TransactionStatus status);

    // Batch lifetime totals for a page of customers at once — one grouped query regardless of page
    // size, instead of one sumAmountByCustomerAndStatus call per row (which is fine for a single
    // customer's statement but would be an N+1 on a list page).
    @Query("""
            SELECT t.customer.id, COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.merchant.id = :merchantId AND t.customer.id IN :customerIds AND t.status = :status
            GROUP BY t.customer.id
            """)
    List<Object[]> sumAmountByCustomerIdsAndStatus(@Param("merchantId") UUID merchantId,
                                                    @Param("customerIds") Collection<UUID> customerIds,
                                                    @Param("status") TransactionStatus status);

    // Statement summary aggregates — always over the customer's full history, independent of
    // whatever from/to/matchStatus filter is applied to the paginated row list above.
    long countByCustomerId(UUID customerId);

    long countByCustomerIdAndStatus(UUID customerId, TransactionStatus status);

    long countByCustomerIdAndMatchStatus(UUID customerId, MatchStatus matchStatus);

    @Query("SELECT MAX(t.receivedAt) FROM Transaction t WHERE t.customer.id = :customerId")
    Instant findLastReceivedAtByCustomerId(@Param("customerId") UUID customerId);

    // Overview reconciliation-health counts — merchant-wide, current snapshot (not date-bounded).
    long countByMerchantIdAndMatchStatus(UUID merchantId, MatchStatus matchStatus);

    long countByMerchantIdAndStatus(UUID merchantId, TransactionStatus status);

    // Daily inflow for the Overview sparkline. Native + date_trunc since JPQL has no portable
    // day-grouping function; only days with at least one row come back, so the service fills the
    // gaps (a quiet day is 0, not a missing point on the chart). The day is formatted as plain
    // 'YYYY-MM-DD' text (to_char), not left as a timestamptz/date value — found live that letting
    // the JDBC driver map a timestamptz result reinterprets it through the JVM's default timezone
    // (not the Postgres session's, which is UTC), silently shifting the day by a day. A plain string
    // has nothing left to reinterpret.
    @Query(value = """
            SELECT to_char(date_trunc('day', received_at), 'YYYY-MM-DD') AS day, COALESCE(SUM(amount), 0) AS total
            FROM transactions
            WHERE merchant_id = :merchantId AND status = 'SUCCESSFUL' AND type = 'CUSTOMER_PAYMENT'
              AND received_at >= :since
            GROUP BY day
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> sumDailyInflowSince(@Param("merchantId") UUID merchantId, @Param("since") Instant since);

    // ---- Platform-wide aggregates (super-admin oversight) ----

    long countByMatchStatus(MatchStatus matchStatus);

    long countByStatus(TransactionStatus status);

    long countByType(TransactionType type);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.type = :type AND t.status = :status
            """)
    BigDecimal sumAmountByTypeAndStatus(@Param("type") TransactionType type,
                                        @Param("status") TransactionStatus status);
}
