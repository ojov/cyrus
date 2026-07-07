package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

/**
 * The JWT itself is never in this body — it's set as an httpOnly cookie on the response (see
 * {@code AuthController}), so client-side JS can never read it.
 */
@Builder
public record MerchantRegistrationResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Unique ID of the registered merchant")
        UUID merchantId,

        @Schema(example = "Cyrus Mobile Ltd", description = "Business name")
        String businessName,

        @Schema(example = "admin@cyrusmobile.com", description = "Business email")
        String businessEmail
) {}
