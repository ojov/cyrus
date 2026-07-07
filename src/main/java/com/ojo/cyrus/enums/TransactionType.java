package com.ojo.cyrus.enums;

/**
 * The kind of movement a {@link com.ojo.cyrus.models.entities.Transaction} represents on Cyrus's
 * books. A single inbound customer payment is a {@code CUSTOMER_PAYMENT}; a merchant withdrawal is
 * a {@code PAYOUT}. REVERSAL/ADJUSTMENT cover clawbacks and manual corrections.
 */
public enum TransactionType {
    CUSTOMER_PAYMENT,
    PAYOUT,
    REVERSAL,
    ADJUSTMENT
}
