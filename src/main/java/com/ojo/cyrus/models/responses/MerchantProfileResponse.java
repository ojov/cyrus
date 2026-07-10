package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "The merchant's current profile")
public record MerchantProfileResponse(
        @Schema(description = "Unique merchant ID")
        UUID merchantId,

        @Schema(description = "Registered business name")
        String businessName,

        @Schema(description = "Business email (login identifier, not editable via this endpoint)")
        String businessEmail,

        @Schema(description = "Type of business")
        String businessType,

        @Schema(description = "Business phone number")
        String phone,

        @Schema(description = "Bank verification number")
        String bankVerificationNumber
) {}
