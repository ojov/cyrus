package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One raw inbound payment event, for the merchant's exceptions/triage list view (no raw payload —
 * see {@link PaymentEventResponse} for that). {@code amount}/{@code accountNumber} let a merchant
 * assess an orphan without opening the detail view; {@code customerReference} is set once an event
 * has been resolved (processed against a known VA, or manually reattributed).
 */
@Schema(description = "A raw inbound payment event, including orphans that couldn't be attributed to a customer")
public record PaymentEventListItem(
        @Schema(description = "Unique event ID")
        UUID id,
        @Schema(description = "Provider's request ID (idempotency key for the raw event)")
        String requestId,
        @Schema(description = "Type of event received from the provider")
        NombaPaymentEventType eventType,
        @Schema(description = "Current processing status of this event")
        NombaPaymentEventStatus status,
        @Schema(description = "If the event could not be processed, why (null otherwise)")
        ReconciliationFailureReason failureReason,
        @Schema(description = "Human-readable details about the processing outcome")
        String statusDetails,
        @Schema(description = "Amount in kobo (if available from the raw payload)")
        BigDecimal amount,
        @Schema(description = "Virtual account number the payment was sent to (if identifiable)")
        String accountNumber,
        @Schema(description = "Customer reference this event was attributed to (null if still orphaned)")
        String customerReference,
        @Schema(description = "When the event was received by Cyrus")
        Instant createdAt
) {}
