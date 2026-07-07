package com.ojo.cyrus.models.dto;

import java.time.Instant;

/** When {@code MerchantWebhookDispatcher} should retry a failed delivery, and which attempt number
 * that retry will be (used to build its deterministic JobRunr id). */
public record RetrySchedule(Instant runAt, int attempts) {}
