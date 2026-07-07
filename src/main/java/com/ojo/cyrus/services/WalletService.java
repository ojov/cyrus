package com.ojo.cyrus.services;

import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Wallet;
import com.ojo.cyrus.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Provisions and reads merchant wallets (one wallet per merchant). Balance <em>mutation</em> lives in
 * {@link LedgerService} — it only ever changes as a side effect of a balanced ledger posting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;

    /** Ensures a wallet exists for the merchant, creating a zero-balance one if not. */
    @Transactional
    public Wallet provisionWallet(Merchant merchant) {
        return walletRepository.findByMerchantId(merchant.getId())
                .orElseGet(() -> {
                    log.info("Provisioning wallet for merchant {}", merchant.getId());
                    return walletRepository.save(Wallet.builder()
                            .merchant(merchant)
                            .availableBalance(BigInteger.ZERO)
                            .build());
                });
    }

    @Transactional(readOnly = true)
    public BigInteger getBalance(UUID merchantId) {
        return walletRepository.findByMerchantId(merchantId)
                .map(Wallet::getAvailableBalance)
                .orElse(BigInteger.ZERO);
    }
}
