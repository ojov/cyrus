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
    PROVIDER_UNCONFIRMED
    // NOTE: MISSING_WEBHOOK was added here (and to the DB CHECK constraint via V14) for an earlier
    // design where MissingWebhookSweepService recorded gaps as merchant-less orphans. That service now
    // replays gaps through the normal ingestion pipeline instead, so no code writes MISSING_WEBHOOK —
    // the value was removed. V14 remains applied (the CHECK constraint permitting an extra, never-
    // written value is harmless); don't re-add the constant without a writer for it.
}
