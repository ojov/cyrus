package com.ojo.cyrus.models.responses;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

public record GeneratedApiKeysResponse(
        @Schema(description = "Set of generated API keys for different environments")
        Set<ApiKeyResponse> apiKeys
) {}
