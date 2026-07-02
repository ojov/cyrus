package com.ojo.cyrus.nomba.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record NombaBalanceData(
        @JsonProperty("amount") String amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("timeCreated") Instant timeCreated
) {
    public BigDecimal parsedAmount() {
        return amount != null ? new BigDecimal(amount) : BigDecimal.ZERO;
    }
}
