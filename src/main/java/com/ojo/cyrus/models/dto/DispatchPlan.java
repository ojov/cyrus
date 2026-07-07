package com.ojo.cyrus.models.dto;

/**
 * Everything {@code MerchantWebhookDispatcher} needs to deliver one webhook, materialized in a
 * short read transaction and carried across the (no-transaction) HTTP POST to the merchant.
 */
public record DispatchPlan(String url, String eventType, String payload, String secret) {}
