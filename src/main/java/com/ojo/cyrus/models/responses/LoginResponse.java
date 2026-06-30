package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record LoginResponse(
        @Schema(example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...", description = "JWT access token")
        String token,

        @Schema(example = "Bearer", description = "Type of the token")
        String tokenType,

        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Unique ID of the merchant")
        UUID merchantId,

        @Schema(example = "Cyrus Mobile Ltd", description = "Name of the business")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "Business email address")
        String businessEmail
) {}
