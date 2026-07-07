package com.ojo.cyrus.models.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Returned after registering or rotating a webhook. {@code secret} is present ONLY on the call that
 * generated it (first registration or an explicit rotate) — it is never retrievable again, so the
 * merchant must store it then. On a URL update that keeps the existing secret, {@code secret} is null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookConfigResponse(
        String url,
        String secret,
        boolean hasSecret
) {}
