package com.ojo.cyrus.enums;

/**
 * Why a {@link com.ojo.cyrus.models.entities.NombaPaymentEvent} was not attributed to a transaction,
 * or why reconciliation could not confirm it. Surfaced to the merchant/ops so an IGNORED or FAILED
 * event is diagnosable without reading the raw payload.
 */
public enum ReconciliationFailureReason {

    /** The credited account number matches no known virtual account (orphan / misdirected payment). */
    UNKNOWN_VIRTUAL_ACCOUNT,

    /** The virtual account's customer is SUSPENDED/CLOSED — routed to the reattribution flow. */
    INACTIVE_CUSTOMER,

    /** Not a VA credit (e.g. POS payment_failed, non-credit event) — recorded but no transaction. */
    NON_CREDIT_EVENT,

    /** A transaction for this provider transaction id already exists. */
    DUPLICATE,

    /** Webhook HMAC signature did not verify. */
    SIGNATURE_MISMATCH,

    /** Nomba's requery reports a different amount than the webhook claimed. */
    AMOUNT_MISMATCH,

    /** Nomba could not confirm the session after the maximum reconciliation attempts. */
    PROVIDER_UNCONFIRMED,

    /**
     * Detected by {@link com.ojo.cyrus.services.MissingWebhookSweepService}: Nomba's sub-account
     * transaction list shows a transaction Cyrus has no local record of at all — the webhook for it
     * was never delivered (or never arrived). Recorded as a merchant-less orphan so it surfaces
     * through the existing super-admin orphan-recovery flow instead of staying invisible forever.
     */
    MISSING_WEBHOOK
}
