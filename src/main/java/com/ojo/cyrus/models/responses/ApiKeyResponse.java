package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.Environment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ApiKeyResponse(
        @Schema(example = "cyrus_test_550e8400e29b41d4a716446655440000", description = "The raw API key (only shown once)")
        String apiKey,

        @Schema(example = "TEST", description = "The environment the key belongs to")
        Environment environment,

        @Schema(example = "2024-03-20T10:00:00Z", description = "When the key was created")
        Instant createdAt
) {}