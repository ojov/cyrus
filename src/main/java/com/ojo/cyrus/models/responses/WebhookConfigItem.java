package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.Environment;

/** One environment's webhook configuration, for the list view. The secret is never exposed. */
public record WebhookConfigItem(
        Environment environment,
        String url,
        boolean hasSecret
) {}
