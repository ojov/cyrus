package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Response shape for {@code GET /v1/transactions/requery/{sessionId}}, confirmed against a live
 * response (2026-07-06). Note the field names differ from the webhook payload's
 * {@code transaction} object: the requery returns {@code id} (not {@code transactionId}),
 * {@code amount} (not {@code transactionAmount}), and {@code fixedCharge} (not {@code fee}).
 * Amounts are naira decimal strings (e.g. {@code "400.0"}) — convert to kobo at this boundary.
 *
 * <p>For a session Nomba doesn't recognize it returns a success envelope with a zeroed/blank
 * transaction rather than an error, so {@code transactionId} (mapped from {@code id}) being
 * non-blank is the only reliable "found" signal — see
 * {@link com.ojo.cyrus.services.ReconciliationService}, which relies on that rather than
 * {@code status}.
 */
public record NombaTransactionData(
        @JsonProperty("id") String transactionId,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("amount") String transactionAmount,
        @JsonProperty("fixedCharge") String fee,
        @JsonProperty("currency") String currency,
        @JsonProperty("status") String status
) {
    public BigInteger amountInKobo() {
        return toKobo(transactionAmount);
    }

    public BigInteger feeInKobo() {
        return toKobo(fee);
    }

    private static BigInteger toKobo(String naira) {
        if (naira == null || naira.isBlank()) {
            return BigInteger.ZERO;
        }
        return new BigDecimal(naira.trim())
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_EVEN)
                .toBigIntegerExact();
    }
}
