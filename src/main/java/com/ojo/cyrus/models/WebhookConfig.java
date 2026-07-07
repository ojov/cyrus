package com.ojo.cyrus.models;

import jakarta.persistence.Embeddable;

/**
 * A merchant's outbound-webhook configuration for one {@link com.ojo.cyrus.enums.Environment}.
 * The signing secret is stored encrypted at rest (AES-256-GCM via
 * {@link com.ojo.cyrus.utils.CryptoUtil}) — never in plaintext.
 */
@Embeddable
public record WebhookConfig(String url, String encryptedSecret) {}
