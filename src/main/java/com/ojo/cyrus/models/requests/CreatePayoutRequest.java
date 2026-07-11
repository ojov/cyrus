package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Initiates a payout from the merchant's wallet to a registered beneficiary. Amount is in kobo and
 * must be a whole number: Nomba's transfer API settles in whole kobo, and rejecting fractional
 * requests here keeps the ledger debit identical to what is actually wired out (see
 * {@code PayoutService.koboToNaira}).
 */
public record CreatePayoutRequest(

        @Schema(description = "The beneficiary to pay out to")
        @NotNull(message = "Beneficiary id is required")
        UUID beneficiaryId,

        @Schema(description = "Amount to pay out, in whole kobo (minor units — no fractional kobo)")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 34, fraction = 0, message = "Amount must be a whole number of kobo")
        BigDecimal amount,

        @Schema(description = "Optional narration shown on the transfer")
        String narration
) {}
