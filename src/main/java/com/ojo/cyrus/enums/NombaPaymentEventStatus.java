package com.ojo.cyrus.enums;

/**
 * Processing state of a persisted {@link com.ojo.cyrus.models.entities.NombaPaymentEvent} (the
 * idempotent, deduplicated record of a raw Nomba webhook). RECEIVED is the landing state; a genuine
 * VA credit becomes PROCESSED (and mints a Transaction); non-credit/orphan/duplicate events become
 * IGNORED/PROCESSED_DUPLICATE with a {@link ReconciliationFailureReason}; REATTRIBUTED marks an
 * orphan a merchant manually attributed to one of their customers.
 */
public enum NombaPaymentEventStatus {
    RECEIVED,
    PROCESSED,
    IGNORED,
    PROCESSED_DUPLICATE,
    FAILED,
    REATTRIBUTED
}
