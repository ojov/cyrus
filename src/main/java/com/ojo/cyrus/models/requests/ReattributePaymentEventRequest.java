package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReattributePaymentEventRequest(
        @Schema(example = "user_123", description = "Your reference for the customer this payment actually belongs to")
        @NotBlank(message = "Customer reference is required")
        String customerReference
) {}
