package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigInteger;
import java.util.UUID;

/** Initiates a payout from the merchant's wallet to a registered beneficiary. Amount is in kobo. */
public record CreatePayoutRequest(

        @Schema(description = "The beneficiary to pay out to")
        @NotNull(message = "Beneficiary id is required")
        UUID beneficiaryId,

        @Schema(description = "Amount to pay out, in kobo (minor units)")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigInteger amount,

        @Schema(description = "Optional narration shown on the transfer")
        String narration
) {}
