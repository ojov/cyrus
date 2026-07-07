package com.ojo.cyrus.models.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.time.Instant;

@Builder
@Getter
public class NormalizedPaymentEvent {

    private String requestId;
    private String eventType;
    private String providerTransactionId;
    private String sessionId;
    // The provider-side wallet the payment landed in — resolves to a Merchant via
    // Merchant.nombaParentAccountId, independent of (and before) virtual-account attribution. This is
    // what lets an orphan payment (unknown VA) still be scoped to the right merchant for recovery.
    private String walletId;
    private String virtualAccountNumber;
    private BigInteger amount; // minor units (kobo)
    private BigInteger fee; // minor units (kobo)
    private String currency;
    private Payer payer;
    private Instant eventTime;

    @Builder
    @Getter
    public static class Payer {
        private String name;
        private String accountNumber;
        private String bankCode;
        private String bankName;
    }

    /**
     * True only for events that credit one of our virtual accounts — a successful VA transfer that
     * carries an alias (virtual) account number. Non-VA / non-success events (e.g. POS purchases,
     * failures) are recorded but never turned into a transaction.
     */
    public boolean isVirtualAccountCredit() {
        return "payment_success".equalsIgnoreCase(eventType)
                && virtualAccountNumber != null && !virtualAccountNumber.isBlank();
    }

    /** A previously successful payment reversed back to the payer — must flip the original transaction. */
    public boolean isReversal() {
        return "payment_reversal".equalsIgnoreCase(eventType);
    }

    /** A payment attempt that never credited us — recorded for visibility, never a transaction. */
    public boolean isFailure() {
        return "payment_failed".equalsIgnoreCase(eventType);
    }

}
