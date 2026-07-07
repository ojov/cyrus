package com.ojo.cyrus.enums;

/**
 * Lifecycle of a merchant {@link com.ojo.cyrus.models.entities.Payout} (withdrawal to a bank
 * beneficiary via Nomba's transfer API).
 */
public enum PayoutStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}
