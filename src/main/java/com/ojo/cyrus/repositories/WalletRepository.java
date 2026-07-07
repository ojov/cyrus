package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByMerchantId(UUID merchantId);

    boolean existsByMerchantId(UUID merchantId);

    /**
     * Pessimistic-write lock for balance mutation — serializes concurrent credit/debit on the same
     * wallet so the running total stays consistent with the ledger. Used inside the posting tx.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.merchant.id = :merchantId")
    Optional<Wallet> findForUpdate(@Param("merchantId") UUID merchantId);
}
