package com.ojo.cyrus.models.requests;

import com.ojo.cyrus.enums.Environment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateApiKeyRequest(
        @Schema(example = "TEST", description = "Environment the key is for (TEST or LIVE)")
        @NotNull(message = "environment is required (TEST or LIVE)")
        Environment environment
) {}
