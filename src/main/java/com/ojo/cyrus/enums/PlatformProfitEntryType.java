package com.ojo.cyrus.enums;

/**
 * The role a single {@link com.ojo.cyrus.models.entities.PlatformProfitEntry} plays in the
 * platform-level profit ledger. Every money movement through Cyrus's Nomba account produces
 * one or more entries whose signed amounts sum to the net effect on the provider balance.
 */
public enum PlatformProfitEntryType {
    /** Confirmed inbound payment received (gross amount credited to Nomba). */
    PROFIT_INFLOW,
    /** Outbound payout submitted to Nomba (debit from Nomba). */
    PROFIT_OUTFLOW,
    /** Cyrus's platform fee earned (merchant fee minus provider fee). */
    PROFIT_FEE_ACCRUAL,
    /** Manual correction applied by a super-admin. */
    PROFIT_ADJUSTMENT
}
