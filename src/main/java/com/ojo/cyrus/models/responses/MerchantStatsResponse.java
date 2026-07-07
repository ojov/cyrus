package com.ojo.cyrus.models.responses;

import java.math.BigInteger;

/** Operational snapshot for the merchant dashboard. Wallet balance is integer kobo. */
public record MerchantStatsResponse(
        long customers,
        long virtualAccounts,
        BigInteger walletBalance
) {}
