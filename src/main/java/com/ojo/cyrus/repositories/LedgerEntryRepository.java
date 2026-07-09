package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    // Wallet ledger statement — newest first, paginated.
    Page<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    // Ledger-integrity check (super-admin): the signed sum of a wallet's entries (credits +, fees/
    // debits −) must equal its stored availableBalance. Returns [walletId, summedAmount] per wallet
    // that has any entries; the service compares against each wallet's balance and flags mismatches.
    @Query("SELECT le.wallet.id, COALESCE(SUM(le.amount), 0) FROM LedgerEntry le GROUP BY le.wallet.id")
    List<Object[]> sumAmountGroupedByWallet();
}
