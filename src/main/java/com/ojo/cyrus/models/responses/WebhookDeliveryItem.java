package com.ojo.cyrus.models.responses;

import com.ojo.cyrus.enums.MerchantWebhookStatus;

import java.time.Instant;
import java.util.UUID;

/** A single outbound-webhook delivery record, for the merchant's delivery-history view. */
public record WebhookDeliveryItem(
        UUID id,
        UUID transactionId,
        String eventType,
        MerchantWebhookStatus status,
        String webhookUrl,
        int attempts,
        Integer lastResponseCode,
        String lastError,
        Instant nextRetryAt,
        Instant deliveredAt,
        Instant createdAt
) {}
