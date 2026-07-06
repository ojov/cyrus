package com.ojo.cyrus.models.dto;

import com.ojo.cyrus.services.MerchantWebhookClient;

/** Result of one webhook delivery attempt, produced by {@link MerchantWebhookClient}. */
public record DeliveryOutcome(boolean success, Integer statusCode, String error, boolean retryable) {
    public static DeliveryOutcome success(int statusCode) {
        return new DeliveryOutcome(true, statusCode, null, false);
    }

   public static DeliveryOutcome retryableFailure(Integer statusCode, String error) {
        return new DeliveryOutcome(false, statusCode, error, true);
    }

   public static DeliveryOutcome permanentFailure(Integer statusCode, String error) {
        return new DeliveryOutcome(false, statusCode, error, false);
    }
}
