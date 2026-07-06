package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for outbound merchant webhook delivery. Delivery retries use exponential backoff:
 * {@code min(initialBackoffSeconds * 2^(attempts-1), maxBackoffSeconds)}.
 */
@ConfigurationProperties(prefix = "app.webhook")
public record WebhookProperties(
        // How many delivery attempts before a webhook is marked FAILED and no longer retried.
        @DefaultValue("5") int maxAttempts,
        // Delay before the first retry; each subsequent retry doubles it (capped at maxBackoffSeconds).
        @DefaultValue("60") long initialBackoffSeconds,
        // Ceiling on the backoff delay so late retries don't drift arbitrarily far out.
        @DefaultValue("3600") long maxBackoffSeconds
) {
    /**
     * Backoff delay (seconds) before the retry that follows the given attempt count. Doubles per
     * attempt, capped at {@code maxBackoffSeconds}. The cap check on each step keeps the doubling
     * from overflowing for any sane {@code maxBackoffSeconds}.
     *
     * @param attempts number of attempts made so far (>= 1)
     */
    public long backoffSecondsFor(int attempts) {
        long delay = initialBackoffSeconds;
        for (int i = 1; i < attempts && delay < maxBackoffSeconds; i++) {
            delay <<= 1;
        }
        return Math.min(delay, maxBackoffSeconds);
    }
}
