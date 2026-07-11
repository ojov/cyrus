package com.ojo.cyrus.models.responses;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of a customer's statement — a single inbound transaction. */
public record StatementRowResponse(
        Instant date,
        String payer,
        String ref,
        String matchStatus,
        BigDecimal amountKobo
) {}
