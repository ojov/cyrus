package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReattributeOrphanRequest(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000",
                description = "The merchant to attribute this orphan payment to")
        @NotNull(message = "Merchant ID is required")
        UUID merchantId,

        @Schema(example = "user_123",
                description = "The customer reference within that merchant this payment actually belongs to")
        @NotBlank(message = "Customer reference is required")
        String customerReference
) {}
