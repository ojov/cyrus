package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/** A single payment event's full detail, including the raw provider payload — for inspecting
 * exactly what was received (e.g. before deciding whether to reattribute an orphan). */
public record PaymentEventResponse(
        UUID id,
        String requestId,
        NombaPaymentEventType eventType,
        NombaPaymentEventStatus status,
        ReconciliationFailureReason failureReason,
        String statusDetails,
        BigInteger amount,
        String accountNumber,
        String customerReference,
        String payload,
        Instant createdAt
) {}
