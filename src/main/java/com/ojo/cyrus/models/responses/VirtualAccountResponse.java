package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record VirtualAccountResponse(

        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Unique ID of the virtual account")
        UUID id,

        @Schema(example = "unique_ref_12345", description = "Your internal customer reference")
        String customerReference,

        @Schema(example = "1234567890", description = "The generated account number")
        String accountNumber,

        @Schema(example = "CYRUS/JOHN DOE", description = "The account name")
        String accountName,

        @Schema(example = "Wema Bank", description = "The bank name")
        String bankName,

        @Schema(example = "ACTIVE", description = "Status of the virtual account")
        String status

) {}