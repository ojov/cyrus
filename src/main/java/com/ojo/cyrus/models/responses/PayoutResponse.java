package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.PayoutStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** A merchant payout. {@code amount}/{@code fee} are integer kobo. */
public record PayoutResponse(
        UUID id,
        String reference,
        PayoutStatus status,
        BigInteger amount,
        BigInteger fee,
        UUID beneficiaryId,
        String providerReference,
        String failureReason,
        Instant createdAt
) {}
