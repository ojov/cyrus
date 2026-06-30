package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.Environment;

import java.time.Instant;

public record ApiKeyResponse(
        String apiKey,
        Environment environment,
        Instant createdAt
) {}