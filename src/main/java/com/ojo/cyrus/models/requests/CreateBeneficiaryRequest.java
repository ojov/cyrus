package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Registers a bank account a merchant can pay out to. Verification against the provider is a hard
 * gate at creation, so the stored account name is always the real, provider-confirmed one — there is
 * no separate nickname; the beneficiary's label is that verified name.
 */
public record CreateBeneficiaryRequest(

        @Schema(description = "Destination account number (NUBAN)")
        @NotBlank(message = "Account number is required")
        String accountNumber,

        @Schema(description = "NIP bank code (from the bank picker, never hand-typed)")
        @NotBlank(message = "Bank code is required")
        String bankCode,

        @Schema(description = "Bank name (from the bank picker, paired with the code)")
        @NotBlank(message = "Bank name is required")
        String bankName
) {}
