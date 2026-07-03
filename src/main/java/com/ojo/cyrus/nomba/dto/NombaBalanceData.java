package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;

public record NombaBalanceData(
        @JsonProperty("amount") String amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("timeCreated") Instant timeCreated
) {
    /**
     * Nomba returns balances in naira (major units) as a decimal string, e.g. "281946.0".
     * Cyrus represents all money as integer kobo (minor units) in a {@link BigInteger},
     * so scale naira up by 100. Sub-kobo precision (should never occur) is rounded half-even.
     */
    public BigInteger amountInKobo() {
        if (amount == null || amount.isBlank()) {
            return BigInteger.ZERO;
        }
        return new BigDecimal(amount.trim())
                .movePointRight(2)                 // naira -> kobo
                .setScale(0, RoundingMode.HALF_EVEN)
                .toBigIntegerExact();
    }
}
