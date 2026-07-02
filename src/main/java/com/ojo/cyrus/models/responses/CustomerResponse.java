package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(

        @Schema(description = "Cyrus-assigned customer ID")
        UUID id,

        @Schema(description = "Your unique reference for this customer")
        String reference,

        String firstName,
        String lastName,
        String email,
        String phoneNumber,

        @Schema(description = "The dedicated virtual account provisioned for this customer")
        VirtualAccountSummary virtualAccount,

        Instant createdAt

) {
    public record VirtualAccountSummary(
            UUID id,
            String accountNumber,
            String accountName,
            String bankName,
            String currency,
            String status
    ) {}
}
