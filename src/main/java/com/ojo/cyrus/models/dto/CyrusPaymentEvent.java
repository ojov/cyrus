package com.ojo.cyrus.models.dto;

import com.ojo.cyrus.enums.Provider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.time.Instant;

@Builder
@Getter
public class CyrusPaymentEvent {

    private Provider provider;
    private String requestId;
    private String eventType;
    private String providerTransactionId;
    private String sessionId;
    private String virtualAccountNumber;
    private BigInteger amount; // minor units (kobo)
    private String currency;
    private Payer payer;
    private Instant eventTime;

    /**
     * True only for events that credit one of our virtual accounts — a successful VA transfer that
     * carries an alias (virtual) account number. Non-VA / non-success events (e.g. POS purchases,
     * failures) are recorded but never turned into a transaction.
     */
    public boolean isVirtualAccountCredit() {
        return "payment_success".equalsIgnoreCase(eventType)
                && virtualAccountNumber != null && !virtualAccountNumber.isBlank();
    }

    @Builder
    @Getter
    public static class Payer {
        private String name;
        private String accountNumber;
        private String bankCode;
        private String bankName;
    }
}
