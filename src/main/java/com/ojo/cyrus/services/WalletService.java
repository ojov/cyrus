package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
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
 * Provisions and reads merchant wallets. A merchant holds one wallet per {@link Environment} (TEST
 * money and LIVE money never mix). Balance <em>mutation</em> lives in {@link LedgerService} — it only
 * ever changes as a side effect of a balanced ledger posting, never directly here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;

    /** Ensures a wallet exists for the merchant + environment, creating a zero-balance one if not. */
    @Transactional
    public Wallet provisionWallet(Merchant merchant, Environment environment) {
        return walletRepository.findByMerchantIdAndEnvironment(merchant.getId(), environment)
                .orElseGet(() -> {
                    log.info("Provisioning {} wallet for merchant {}", environment, merchant.getId());
                    return walletRepository.save(Wallet.builder()
                            .merchant(merchant)
                            .environment(environment)
                            .availableBalance(BigInteger.ZERO)
                            .build());
                });
    }

    /** Provisions both TEST and LIVE wallets (called at merchant registration). */
    @Transactional
    public void provisionWallets(Merchant merchant) {
        for (Environment env : Environment.values()) {
            provisionWallet(merchant, env);
        }
    }

    @Transactional(readOnly = true)
    public BigInteger getBalance(UUID merchantId, Environment environment) {
        return walletRepository.findByMerchantIdAndEnvironment(merchantId, environment)
                .map(Wallet::getAvailableBalance)
                .orElse(BigInteger.ZERO);
    }
}
