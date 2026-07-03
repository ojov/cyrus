package com.ojo.cyrus.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Supplementary, client-safe error detail attached to the {@code data} field of a failed
 * {@code CyrusApiResponse}. Present only when it adds information beyond the envelope
 * (code / description / message): per-field validation errors, or a correlation id for a
 * server-side failure. Never carries stack traces, provider payloads, or internal messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetails(
        @Schema(description = "Per-field validation errors, when applicable")
        List<FieldError> fieldErrors,

        @Schema(example = "3f9a1c7e", description = "Correlation id — quote this to support; it matches the server log entry for this error")
        String traceId
) {
    public record FieldError(
            @Schema(example = "email") String field,
            @Schema(example = "must be a valid email address") String message) {
    }

    public static ErrorDetails ofFieldErrors(List<FieldError> fieldErrors) {
        return new ErrorDetails(fieldErrors, null);
    }

    public static ErrorDetails ofTrace(String traceId) {
        return new ErrorDetails(null, traceId);
    }
}
