package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * One raw inbound payment event, for the merchant's exceptions/triage list view (no raw payload —
 * see {@link PaymentEventResponse} for that). {@code amount}/{@code accountNumber} let a merchant
 * assess an orphan without opening the detail view; {@code customerReference} is set once an event
 * has been resolved (processed against a known VA, or manually reattributed).
 */
public record PaymentEventListItem(
        UUID id,
        String requestId,
        NombaPaymentEventType eventType,
        NombaPaymentEventStatus status,
        ReconciliationFailureReason failureReason,
        String statusDetails,
        BigInteger amount,
        String accountNumber,
        String customerReference,
        Instant createdAt
) {}
