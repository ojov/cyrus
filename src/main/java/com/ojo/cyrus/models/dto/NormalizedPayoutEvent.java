package com.ojo.cyrus.models.dto;

import java.math.BigInteger;

/**
 * A Nomba {@code payout_success}/{@code payout_failed}/{@code payout_refund} webhook, normalized off
 * the raw provider payload by {@code NombaWebhookAdapter#toPayoutEvent}. Sibling of
 * {@link NormalizedPaymentEvent} but for outbound transfers: it finalizes an existing
 * {@code Payout}, matched by {@link #merchantTxRef} (the value Cyrus sent as the transfer's
 * {@code merchantTxRef}, which is the payout's own reference). Amounts are integer kobo.
 *
 * @param eventType             raw Nomba {@code event_type} (payout_success/failed/refund)
 * @param requestId             Nomba's unique event id (idempotency + audit)
 * @param merchantTxRef         echoes back the payout reference we submitted
 * @param providerTransactionId Nomba's transfer id (stored as the payout's providerReference)
 * @param sessionId             Nomba session id
 * @param feeKobo               transfer fee in kobo
 * @param amountKobo            transfer amount in kobo
 */
public record NormalizedPayoutEvent(
        String eventType,
        String requestId,
        String merchantTxRef,
        String providerTransactionId,
        String sessionId,
        BigInteger feeKobo,
        BigInteger amountKobo
) {
    public boolean isSuccess() {
        return "payout_success".equalsIgnoreCase(eventType);
    }

    /** Both a failed and a refunded payout return the reserved funds to the wallet. */
    public boolean isFailureOrRefund() {
        return "payout_failed".equalsIgnoreCase(eventType) || "payout_refund".equalsIgnoreCase(eventType);
    }
}
