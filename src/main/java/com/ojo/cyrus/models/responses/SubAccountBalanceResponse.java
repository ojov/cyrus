package com.ojo.cyrus.models.responses;

import java.math.BigInteger;
import java.time.Instant;

public record SubAccountBalanceResponse(
        String accountId,
        String accountType,
        BigInteger balance,   // integer kobo (minor units); convert to naira at the display edge
        String currency,
        Instant asOf
) {}
