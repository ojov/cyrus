package com.ojo.cyrus.enums;

/**
 * Lifecycle of an inbound payment as understood by Cyrus.
 */
public enum TransactionStatus {

    /**
     * Webhook received and persisted, but the transaction has not yet been
     * independently confirmed against Nomba via the transaction requery API.
     */
    PENDING,

    /**
     * The payment has been successfully confirmed by Nomba and is considered
     * final from Cyrus's perspective.
     */
    SUCCESSFUL,

    /**
     * The payment could not be confirmed or ultimately failed after
     * reconciliation attempts.
     */
    FAILED,

    /**
     * A previously successful payment was reversed by Nomba.
     */
    REVERSED
}
