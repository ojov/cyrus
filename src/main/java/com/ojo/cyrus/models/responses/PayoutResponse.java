package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.PayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A merchant payout. {@code amount}/{@code fee} are integer kobo. */
public record PayoutResponse(
        UUID id,
        String reference,
        PayoutStatus status,
        BigDecimal amount,
        @Schema(description = """
                Cyrus's flat fee for this payout in kobo. Always ₦30 (3000 kobo) — a fixed charge
                that covers Nomba's ₦20 transfer fee plus a ₦10 Cyrus margin.
                The total debit from your wallet = amount + fee.""")
        BigDecimal fee,
        UUID beneficiaryId,
        String providerReference,
        String failureReason,
        Instant createdAt
) {}
