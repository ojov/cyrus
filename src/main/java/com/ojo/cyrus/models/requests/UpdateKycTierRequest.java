package com.ojo.cyrus.models.requests;

import com.ojo.cyrus.enums.KycTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Sets the customer's KYC tier. Cyrus doesn't verify KYC itself — that's the merchant's own
 * process (their own KYC provider, BVN checks, etc.) — this endpoint just records the outcome
 * whenever the merchant decides to call it. The virtual account is unaffected either way.
 */
public record UpdateKycTierRequest(
        @Schema(description = "The tier to set")
        @NotNull(message = "Tier is required")
        KycTier tier
) {}
