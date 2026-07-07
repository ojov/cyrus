package com.ojo.cyrus.models.responses;

import java.math.BigInteger;

/** Operational snapshot for the merchant dashboard. Wallet balances are integer kobo. */
public record MerchantStatsResponse(
        long customers,
        long virtualAccounts,
        BigInteger testWalletBalance,
        BigInteger liveWalletBalance
) {}
