package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.models.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByProviderAndProviderTransactionId(Provider provider, String providerTransactionId);

    Optional<Transaction> findByProviderAndProviderTransactionId(Provider provider, String providerTransactionId);

    // Fallback match for a reversal whose transactionId doesn't line up with the original —
    // sessionId is the other stable identifier Nomba carries across the transfer's lifecycle.
    Optional<Transaction> findByProviderAndSessionId(Provider provider, String sessionId);
}
