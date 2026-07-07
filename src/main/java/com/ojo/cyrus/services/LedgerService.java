package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.exception.InsufficientFundsException;
import com.ojo.cyrus.models.entities.LedgerEntry;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.Wallet;
import com.ojo.cyrus.repositories.LedgerEntryRepository;
import com.ojo.cyrus.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

/**
 * The single writer of wallet balances: every change goes through a balanced double-entry posting so
 * the wallet balance is always a materialized running total of the append-only {@link LedgerEntry}
 * trail. Callers must invoke these inside their own transaction (e.g. atomic with promoting a payment
 * to SUCCESSFUL) — the wallet row is pessimistically locked for the duration so concurrent
 * credit/debit serialize instead of racing on the balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /** Credits the merchant's wallet by {@code amount} (kobo) and records the ledger entry. */
    public LedgerEntry credit(Merchant merchant, Environment env, BigInteger amount, Transaction transaction,
                              LedgerEntryType type, String description) {
        return post(merchant, env, amount.abs(), transaction, type, description);
    }

    /** Debits the merchant's wallet by {@code amount} (kobo); throws if it would go negative. */
    public LedgerEntry debit(Merchant merchant, Environment env, BigInteger amount, Transaction transaction,
                             LedgerEntryType type, String description) {
        return post(merchant, env, amount.abs().negate(), transaction, type, description);
    }

    /**
     * Applies a signed {@code delta} (positive credit / negative debit) to the wallet and appends a
     * matching ledger entry. The wallet is locked FOR UPDATE so the read-modify-write is serialized.
     */
    private LedgerEntry post(Merchant merchant, Environment env, BigInteger delta, Transaction transaction,
                             LedgerEntryType type, String description) {
        Wallet wallet = walletRepository.findForUpdate(merchant.getId(), env)
                .orElseThrow(() -> new IllegalStateException(
                        "No " + env + " wallet for merchant " + merchant.getId()));

        BigInteger newBalance = wallet.getAvailableBalance().add(delta);
        if (newBalance.signum() < 0) {
            throw new InsufficientFundsException("Insufficient wallet balance for this operation");
        }
        wallet.setAvailableBalance(newBalance);

        LedgerEntry entry = ledgerEntryRepository.save(LedgerEntry.builder()
                .transaction(transaction)
                .wallet(wallet)
                .amount(delta)
                .type(type)
                .description(description)
                .build());

        log.info("Ledger {} {} kobo on merchant {} {} wallet -> balance {}",
                type, delta, merchant.getId(), env, newBalance);
        return entry;
    }
}
