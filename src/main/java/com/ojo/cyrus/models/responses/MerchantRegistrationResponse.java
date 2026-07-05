package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;
@Builder
public record MerchantRegistrationResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Unique ID of the registered merchant")
        UUID merchantId,

        @Schema(example = "Cyrus Mobile Ltd", description = "Business name")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "Business email")
        String businessEmail,

        @Schema(example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...", description = "JWT for immediate dashboard access")
        String token
) {}