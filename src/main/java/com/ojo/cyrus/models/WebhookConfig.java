package com.ojo.cyrus.models;

import jakarta.persistence.Embeddable;

/**
 * A merchant's outbound-webhook configuration (URL + signing secret). The secret is stored encrypted
 * at rest (AES-256-GCM via {@link com.ojo.cyrus.utils.CryptoUtil}) — never in plaintext.
 */
@Embeddable
public record WebhookConfig(String url, String encryptedSecret) {}
