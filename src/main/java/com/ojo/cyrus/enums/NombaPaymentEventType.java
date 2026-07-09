package com.ojo.cyrus.enums;

import java.util.Arrays;

/**
 * Normalized classification of an inbound Nomba webhook event. The raw Nomba {@code event_type}
 * string (e.g. {@code payment_success}, {@code payment_failed}) is mapped onto one of these by
 * {@link com.ojo.cyrus.nomba.NombaWebhookAdapter}; anything unrecognized becomes {@link #UNKNOWN}.
 */
public enum NombaPaymentEventType {

    PAYMENT_SUCCESS("payment_success"),
    PAYMENT_FAILED("payment_failed"),
    PAYMENT_REVERSED("payment_reversed"),
    // Outbound-transfer (payout) outcomes — the authoritative result of a merchant payout, matched
    // back to a Payout by transaction.merchantTxRef. Refund = previously-sent funds returned.
    PAYOUT_SUCCESS("payout_success"),
    PAYOUT_FAILED("payout_failed"),
    PAYOUT_REFUND("payout_refund"),
    UNKNOWN("");

    private final String wireName;

    NombaPaymentEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** True for the payout-outcome events, which finalize a {@code Payout} rather than a payment. */
    public boolean isPayout() {
        return this == PAYOUT_SUCCESS || this == PAYOUT_FAILED || this == PAYOUT_REFUND;
    }

    /** Maps Nomba's raw event_type string onto a normalized type, defaulting to {@link #UNKNOWN}. */
    public static NombaPaymentEventType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(t -> t.wireName.equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
