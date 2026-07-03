package com.ojo.cyrus.repositories;

import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.models.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByProviderAndProviderTransactionId(Provider provider, String providerTransactionId);
}
