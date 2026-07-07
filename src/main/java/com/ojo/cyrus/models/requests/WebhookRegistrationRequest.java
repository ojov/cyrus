package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WebhookRegistrationRequest(
        @Schema(example = "https://api.yourapp.com/webhooks/cyrus",
                description = "HTTPS URL Cyrus will POST payment events to")
        @NotBlank(message = "Webhook URL is required")
        @Pattern(regexp = "^https?://.+", message = "Webhook URL must start with http:// or https://")
        String url
) {}
