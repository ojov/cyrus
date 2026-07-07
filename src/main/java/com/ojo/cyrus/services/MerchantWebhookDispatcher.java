package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.config.properties.WebhookProperties;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.models.WebhookConfig;
import com.ojo.cyrus.models.dto.DeliveryOutcome;
import com.ojo.cyrus.models.dto.DispatchPlan;
import com.ojo.cyrus.models.dto.RetrySchedule;
import com.ojo.cyrus.models.entities.MerchantWebhookEvent;
import com.ojo.cyrus.repositories.MerchantWebhookEventRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates delivery of one outbound merchant webhook. Invoked as a JobRunr job (enqueued by
 * {@link MerchantWebhookService} once the outbox row is committed). Each run: materialize the
 * target/secret/payload in a short read tx, hand the actual POST off to {@link MerchantWebhookClient}
 * with NO transaction open (merchant endpoints are slow third parties), then record the outcome in
 * a short write tx. Transient failures — 5xx, 429, timeouts, DNS/connection errors — are retried
 * with exponential backoff up to {@code app.webhook.max-attempts}. Other 4xx (400/401/403/404/410
 * etc.) mean the request itself is rejected and retrying the identical payload will fail identically
 * every time, so those are marked FAILED immediately without burning through the retry schedule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantWebhookDispatcher {

    private static final int MAX_ERROR_LEN = 500;

    private final MerchantWebhookEventRepository webhookEventRepository;
    private final MerchantWebhookClient webhookClient;
    private final PlatformTransactionManager transactionManager;
    private final JobScheduler jobScheduler;
    private final AppProperties appProperties;
    private final WebhookProperties webhookProperties;

    /** Deterministic JobRunr id for the initial delivery of a webhook event (idempotent enqueue). */
    public static UUID initialJobId(UUID webhookEventId) {
        return UUID.nameUUIDFromBytes(("webhook:" + webhookEventId).getBytes(StandardCharsets.UTF_8));
    }

    /** Distinct id per retry so a retry never collides with the currently-running job's id. */
    private static UUID retryJobId(UUID webhookEventId, int attempts) {
        return UUID.nameUUIDFromBytes(
                ("webhook:" + webhookEventId + ":retry:" + attempts).getBytes(StandardCharsets.UTF_8));
    }

    public void dispatch(UUID webhookEventId) {
        DispatchPlan plan = loadPlan(webhookEventId);
        if (plan == null) {
            return; // event missing, already delivered, or config gone (FAILED recorded in loadPlan)
        }

        DeliveryOutcome outcome = webhookClient.send(
                plan.url(), plan.eventType(), webhookEventId, plan.payload(), plan.secret());
        recordOutcome(webhookEventId, outcome);
    }

    /**
     * Reads everything the HTTP call needs inside one short transaction and decrypts the merchant's
     * current signing secret (so a rotated secret applies to in-flight retries). Returns {@code null}
     * when there is nothing to deliver.
     */
    private DispatchPlan loadPlan(UUID webhookEventId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            MerchantWebhookEvent event = webhookEventRepository.findById(webhookEventId).orElse(null);
            if (event == null) {
                log.warn("Webhook event {} not found — nothing to dispatch", webhookEventId);
                return null;
            }
            if (event.getStatus() == MerchantWebhookStatus.DELIVERED) {
                return null; // already delivered; idempotent no-op
            }

            // Sign with the merchant's CURRENT secret (rotation applies to in-flight retries).
            // Deliver to the URL snapshotted on the event.
            WebhookConfig config = event.getMerchant().getWebhookConfig();
            if (config == null || config.encryptedSecret() == null) {
                event.setStatus(MerchantWebhookStatus.FAILED);
                event.setLastError("No webhook configuration on the merchant");
                log.warn("Webhook event {} has no config on the merchant — marking FAILED", webhookEventId);
                return null;
            }

            String secret = CryptoUtil.decrypt(config.encryptedSecret(), appProperties.encryptionKey());
            return new DispatchPlan(event.getWebhookUrl(), event.getEventType(), event.getPayload(), secret);
        });
    }

    /** Persist the delivery result; on a retryable failure, schedule the next attempt with backoff. */
    private void recordOutcome(UUID webhookEventId, DeliveryOutcome outcome) {
        RetrySchedule retry = new TransactionTemplate(transactionManager).execute(status ->
                webhookEventRepository.findById(webhookEventId).map(event -> {
                    if (outcome.success()) {
                        event.setStatus(MerchantWebhookStatus.DELIVERED);
                        event.setDeliveredAt(Instant.now());
                        event.setLastResponseCode(outcome.statusCode());
                        event.setLastError(null);
                        event.setNextRetryAt(null);
                        log.info("Webhook {} delivered (HTTP {})", webhookEventId, outcome.statusCode());
                        return null;
                    }

                    int attempts = event.getAttempts() + 1;
                    event.setAttempts(attempts);
                    event.setLastResponseCode(outcome.statusCode());
                    event.setLastError(truncate(outcome.error()));

                    if (!outcome.retryable() || attempts >= webhookProperties.maxAttempts()) {
                        event.setStatus(MerchantWebhookStatus.FAILED);
                        event.setNextRetryAt(null);
                        log.warn("Webhook {} FAILED after {} attempt(s) (last HTTP {}, retryable={})",
                                webhookEventId, attempts, outcome.statusCode(), outcome.retryable());
                        return null;
                    }

                    Instant next = Instant.now().plusSeconds(webhookProperties.backoffSecondsFor(attempts));
                    event.setStatus(MerchantWebhookStatus.RETRYING);
                    event.setNextRetryAt(next);
                    log.info("Webhook {} delivery failed (attempt {}, HTTP {}) — retrying at {}",
                            webhookEventId, attempts, outcome.statusCode(), next);
                    return new RetrySchedule(next, attempts);
                }).orElse(null));

        if (retry != null) {
            // The event row committed above; schedule the retry with an id distinct from this job's.
            jobScheduler.schedule(retryJobId(webhookEventId, retry.attempts()), retry.runAt(),
                    () -> dispatch(webhookEventId));
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
