package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Registers a bank account a merchant can pay out to. The account name is verified against Nomba. */
public record CreateBeneficiaryRequest(

        @Schema(description = "A friendly label for this beneficiary", example = "Main GTBank")
        @NotBlank(message = "Nickname is required")
        String nickname,

        @Schema(description = "Destination account number (NUBAN)")
        @NotBlank(message = "Account number is required")
        String accountNumber,

        @Schema(description = "NIP bank code")
        @NotBlank(message = "Bank code is required")
        String bankCode,

        @Schema(description = "Bank name")
        @NotBlank(message = "Bank name is required")
        String bankName
) {}
