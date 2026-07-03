package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.ApiKeyStatus;
import com.ojo.cyrus.enums.Environment;

import java.time.Instant;
import java.util.UUID;

/** A merchant's API key as shown in the dashboard — metadata only, never the raw key (it's hashed). */
public record ApiKeyListItem(
        UUID id,
        String prefix,
        Environment environment,
        ApiKeyStatus status,
        Instant createdAt,
        Instant lastUsedAt
) {}
