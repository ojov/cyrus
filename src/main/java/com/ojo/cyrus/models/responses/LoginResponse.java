package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * The JWT itself is never in this body — it's set as an httpOnly cookie on the response (see
 * {@code AuthController}), so client-side JS can never read it.
 */
public record LoginResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Unique ID of the merchant")
        UUID merchantId,

        @Schema(example = "Cyrus Mobile Ltd", description = "Name of the business")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "Business email address")
        String businessEmail
) {}
