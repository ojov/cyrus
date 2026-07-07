package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;

import java.time.Instant;
import java.util.UUID;

/** One raw inbound payment event, for the merchant's payment-events list view (no raw payload). */
public record PaymentEventListItem(
        UUID id,
        String requestId,
        NombaPaymentEventType eventType,
        NombaPaymentEventStatus status,
        String statusDetails,
        Instant createdAt
) {}
