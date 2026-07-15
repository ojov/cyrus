package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Dashboard-only wrapper around {@link CustomerResponse} that adds a live authenticity check of the
 * customer's virtual account against Nomba directly — not exposed on the API-key developer surface
 * ({@code GET /v1/customers/{reference}}), which stays a plain, fast local-only read.
 */
public record CustomerDetailResponse(
        CustomerResponse customer,
        NombaVerification nombaVerification
) {
    public record NombaVerification(
            @Schema(description = "False if the live Nomba call couldn't be completed (network/provider error) — verification was skipped, not failed")
            boolean checked,

            @Schema(description = "True if checked and the local record matches Nomba exactly")
            boolean matched,

            @Schema(description = "Human-readable mismatches between the local record and Nomba's live data; empty when matched")
            List<String> discrepancies,

            @Schema(description = "True if this result was served from the short-lived cache rather than a fresh Nomba call")
            boolean fromCache,

            Instant checkedAt
    ) {}
}
