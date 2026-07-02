package com.ojo.cyrus.models.responses;

import java.math.BigDecimal;
import java.time.Instant;

public record SubAccountBalanceResponse(
        String accountId,
        String accountType,
        BigDecimal balance,
        String currency,
        Instant asOf
) {}
