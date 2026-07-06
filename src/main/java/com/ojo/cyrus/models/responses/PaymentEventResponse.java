package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;

import java.time.Instant;
import java.util.UUID;

/** A single payment event's full detail, including the raw provider payload — for inspecting
 * exactly what was received (e.g. before deciding whether to reattribute an orphan). */
public record PaymentEventResponse(
        UUID id,
        String requestId,
        Provider provider,
        String eventType,
        EventStatus status,
        String statusDetails,
        String payload,
        Instant createdAt
) {}
