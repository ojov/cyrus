package com.ojo.cyrus.enums;

/**
 * The role a single {@link com.ojo.cyrus.models.entities.LedgerEntry} plays in a double-entry
 * posting. Every money movement produces balanced entries whose types sum to zero against the
 * merchant wallet (e.g. a customer payment credits {@code MERCHANT_WALLET_CREDIT} and may debit a
 * {@code PROVIDER_FEE}/{@code PLATFORM_FEE}).
 */
public enum LedgerEntryType {
    MERCHANT_WALLET_CREDIT,
    MERCHANT_WALLET_DEBIT,
    PROVIDER_FEE,
    PLATFORM_FEE,
    PAYOUT,
    PAYOUT_FEE,
    REVERSAL,
    ADJUSTMENT
}
