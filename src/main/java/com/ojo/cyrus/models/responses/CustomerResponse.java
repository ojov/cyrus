package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.enums.MerchantCustomerStatus;
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
        MerchantCustomerStatus status,
        KycTier kycTier,

        @Schema(description = "The dedicated virtual account provisioned for this customer")
        VirtualAccountSummary virtualAccount,

        Instant createdAt

) {
    public record VirtualAccountSummary(
            @Schema(description = "Cyrus-assigned virtual account ID")
            UUID id,
            @Schema(description = "Nuban account number — payers send transfers here")
            String accountNumber,
            @Schema(description = "Account holder name (matches the customer's name)")
            String accountName,
            @Schema(description = "Bank name (e.g. Wema Bank)")
            String bankName,
            @Schema(description = "Account currency (always NGN)")
            String currency,
            @Schema(description = "Account status: ACTIVE, SUSPENDED, or CLOSED")
            String status
    ) {}
}
