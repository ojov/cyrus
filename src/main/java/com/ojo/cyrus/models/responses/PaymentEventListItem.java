package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;

import java.time.Instant;
import java.util.UUID;

/** One raw inbound payment event, for the merchant's payment-events list view (no raw payload). */
public record PaymentEventListItem(
        UUID id,
        String requestId,
        Provider provider,
        String eventType,
        EventStatus status,
        String statusDetails,
        Instant createdAt
) {}
