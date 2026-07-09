package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Verifies a destination bank account before it's saved as a beneficiary. The merchant picks a bank
 * (supplying its NIP code) and enters an account number; the resolved account name is returned so
 * they can confirm it before adding.
 */
public record VerifyBeneficiaryRequest(

        @Schema(description = "Destination account number (NUBAN)")
        @NotBlank(message = "Account number is required")
        String accountNumber,

        @Schema(description = "NIP bank code (from the bank picker, never hand-typed)")
        @NotBlank(message = "Bank code is required")
        String bankCode
) {}
