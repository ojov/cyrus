package com.ojo.cyrus.models.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoLiveRequest(
        @Schema(description = "Your LIVE Nomba client ID")
        @NotBlank(message = "Live Nomba client ID is required")
        String nombaClientId,

        @Schema(description = "Your LIVE Nomba client secret")
        @NotBlank(message = "Live Nomba client secret is required")
        String nombaClientSecret
) {}
