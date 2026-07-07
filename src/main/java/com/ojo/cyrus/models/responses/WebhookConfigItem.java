package com.ojo.cyrus.models.responses;

/** The merchant's webhook configuration, for the list view. The secret is never exposed. */
public record WebhookConfigItem(
        String url,
        boolean hasSecret
) {}
