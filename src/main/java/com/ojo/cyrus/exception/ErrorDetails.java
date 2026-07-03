package com.ojo.cyrus.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Client-safe error payload returned in the {@code data} field of a failed
 * {@code CyrusApiResponse}. Only ever carries information that is safe to expose to API
 * consumers — never stack traces, provider payloads, or internal messages. Server-side
 * failures include a {@link #traceId} that matches the logged entry so support can correlate
 * a user-reported error with the actual exception.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetails(
        @Schema(example = "INVALID_INPUT", description = "Stable machine-readable error reason")
        String reason,

        @Schema(example = "One or more fields are invalid", description = "Human-readable, safe-to-display message")
        String message,

        @Schema(description = "Per-field validation errors, when applicable")
        List<FieldError> fieldErrors,

        @Schema(example = "3f9a1c7e", description = "Correlation id — quote this to support; it matches the server log entry for this error")
        String traceId,

        @Schema(example = "2024-03-20T10:00:00Z", description = "When the error occurred")
        Instant timestamp
) {
    public record FieldError(
            @Schema(example = "email") String field,
            @Schema(example = "must be a valid email address") String message) {
    }

    public static ErrorDetails of(String reason, String message) {
        return new ErrorDetails(reason, message, null, null, Instant.now());
    }

    public static ErrorDetails of(String reason, String message, String traceId) {
        return new ErrorDetails(reason, message, null, traceId, Instant.now());
    }

    public static ErrorDetails validation(String reason, String message, List<FieldError> fieldErrors) {
        return new ErrorDetails(reason, message, fieldErrors, null, Instant.now());
    }
}
