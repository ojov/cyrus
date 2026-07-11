package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A single payment event's full detail, including the raw provider payload — for inspecting
 * exactly what was received (e.g. before deciding whether to reattribute an orphan). */
@Schema(description = "Full detail of a raw inbound payment event, including the provider's original payload")
public record PaymentEventResponse(
        @Schema(description = "Unique event ID")
        UUID id,
        @Schema(description = "Provider's request ID (idempotency key)")
        String requestId,
        @Schema(description = "Type of event received from the provider")
        NombaPaymentEventType eventType,
        @Schema(description = "Current processing status")
        NombaPaymentEventStatus status,
        @Schema(description = "Why processing failed (null if successful)")
        ReconciliationFailureReason failureReason,
        @Schema(description = "Human-readable details about the outcome")
        String statusDetails,
        @Schema(description = "Amount in kobo")
        BigDecimal amount,
        @Schema(description = "Virtual account number the payment was sent to (if identifiable)")
        String accountNumber,
        @Schema(description = "Customer reference this event was attributed to (null if orphaned)")
        String customerReference,
        @Schema(description = "Raw JSON payload as received from the provider")
        String payload,
        @Schema(description = "When the event was received by Cyrus")
        Instant createdAt
) {}
