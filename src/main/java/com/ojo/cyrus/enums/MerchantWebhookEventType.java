package com.ojo.cyrus.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The outbound webhook events Cyrus emits to merchants. {@code wireName} is the stable string sent
 * on the wire (in the payload's {@code event} field and the {@code X-Cyrus-Event} header) and
 * persisted on {@link com.ojo.cyrus.models.entities.MerchantWebhookEvent#getEventType()}.
 */
@Getter
@RequiredArgsConstructor
public enum MerchantWebhookEventType {

    // A transaction was confirmed by Nomba's requery (MATCHED or DISCREPANCY — the money exists
    // either way; a DISCREPANCY is a reconciliation concern surfaced via matchStatus, not a
    // separate payment event).
    PAYMENT_SUCCEEDED("payment.succeeded"),

    // A previously recorded transaction was clawed back (REVERSED).
    PAYMENT_REVERSED("payment.reversed"),

    // Reconciliation could not confirm the transaction after the maximum attempts and flagged it
    // MANUAL_REVIEW for a human to check.
    PAYMENT_FLAGGED("payment.flagged");

    private final String wireName;
}
