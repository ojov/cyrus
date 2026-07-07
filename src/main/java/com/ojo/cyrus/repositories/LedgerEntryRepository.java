package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    // Wallet ledger statement — newest first, paginated.
    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
