package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByProviderAndProviderTransactionId(Provider provider, String providerTransactionId);

    Optional<Transaction> findByProviderAndProviderTransactionId(Provider provider, String providerTransactionId);

    // Fetch by webhook requestId for direct reconciliation lookup.
    Optional<Transaction> findByRequestId(String requestId);

    // Fallback match for a reversal whose transactionId doesn't line up with the original —
    // sessionId is the other stable identifier Nomba carries across the transfer's lifecycle.
    Optional<Transaction> findByProviderAndSessionId(Provider provider, String sessionId);

    // Reconciliation candidates: unmatched transactions past the grace period (skip anything too
    // recent — the webhook may just not have caught up yet).
    List<Transaction> findByMatchStatusAndReceivedAtBefore(MatchStatus matchStatus, Instant cutoff);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.merchant.id = :merchantId AND t.environment = :environment AND t.status = :status
            """)
    BigInteger sumAmountByMerchantAndEnvironmentAndStatus(@Param("merchantId") UUID merchantId,
                                                           @Param("environment") Environment environment,
                                                           @Param("status") TransactionStatus status);
}
