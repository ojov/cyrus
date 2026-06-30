package com.ojo.cyrus.models.responses;

import java.util.Set;

public record GeneratedApiKeysResponse(
        Set<ApiKeyResponse> apiKeys
) {}
