package com.ojo.cyrus.nomba.service;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.exception.WebhookSignatureException;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.dto.NormalizedPayoutEvent;
import com.ojo.cyrus.models.entities.NombaPaymentEvent;
import com.ojo.cyrus.nomba.NombaWebhookAdapter;
import com.ojo.cyrus.services.PaymentEventService;
import com.ojo.cyrus.services.PayoutService;
import com.ojo.cyrus.services.TransactionIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates inbound Nomba webhooks: verify signature (security boundary) → map to a
 * provider-agnostic event → ingest. No DB transaction is held across verification/parsing;
 * the persistence happens in {@link TransactionIngestionService#ingest}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NombaWebhookService {

    private final NombaSignatureService signatureService;
    private final NombaWebhookAdapter adapter;
    private final TransactionIngestionService ingestionService;
    private final PaymentEventService paymentEventService;
    private final PayoutService payoutService;
    private final ObjectMapper objectMapper;

    public void handle(String signature, String timestamp, String rawPayload) {
        // 1. Verify first — reject anything that isn't authentically from Nomba.
        if (!signatureService.isValid(rawPayload, signature, timestamp)) {
            throw new WebhookSignatureException("Invalid Nomba webhook signature");
        }
        JsonNode root = objectMapper.readTree(rawPayload);

        String requestId = root.path("requestId").asText();
        String eventType = root.path("event_type").asText();
        log.info("Accepted Nomba webhook requestId={} event={}", requestId, eventType);
        // Logged only after signature verification (so this is always a genuine Nomba payload, never
        // arbitrary unauthenticated input) — lets a real prod payload be pulled straight from GCP Cloud
        // Logging and replayed as a local mock, without needing prod DB access (the same payload is also
        // durably persisted in nomba_payment_events.raw_payload for later reference).
        log.info("Nomba webhook payload requestId={} rawPayload={}", requestId, rawPayload);

        // 2a. Payout outcome (payout_success/failed/refund) finalizes an existing Payout — a separate
        //     concern from payment ingestion. Branch here and return; the payment path below is
        //     untouched. (Payment ingestion flow must not change — see AGENTS.md.)
        if (NombaPaymentEventType.fromWire(eventType).isPayout()) {
            handlePayout(requestId, eventType, rawPayload);
            return;
        }

        // 2b. Normalize the raw provider payload into a Cyrus payment event.
        NormalizedPaymentEvent event;
        try {
            event = adapter.toCyrusEvent(rawPayload);
        } catch (NombaIntegrationException e) {
            // An unexpected payload shape is a bug in our own parsing, not a transient failure —
            // retrying the identical payload for Nomba's ~53-minute backoff window can't fix it.
            // Record what we can and return normally so the delivery isn't retried into a storm.
            recordUnparseable(rawPayload, e);
            return;
        }

        // 3. Record + attribute (idempotent, transactional). Ingestion itself schedules the
        //    delayed reconciliation requery (afterCommit) for any transaction it mints.
        ingestionService.ingest(event, rawPayload);
    }

    /**
     * Finalizes a payout from its Nomba webhook: persist a durable audit row (idempotent by
     * requestId), parse, then hand to {@link PayoutService#applyWebhook} (which locks + status-guards
     * the payout, so a duplicate/late delivery is a safe no-op). A malformed payload can't be fixed
     * by retrying the identical body, so it's logged and swallowed (no retry storm); a transient
     * failure inside {@code applyWebhook} propagates so Nomba retries.
     */
    private void handlePayout(String requestId, String eventType, String rawPayload) {
        paymentEventService.recordPayoutEvent(requestId, eventType, rawPayload);

        NormalizedPayoutEvent event;
        try {
            event = adapter.toPayoutEvent(rawPayload);
        } catch (NombaIntegrationException e) {
            log.error("Unparseable payout webhook requestId={} eventType={}: {}",
                    requestId, eventType, e.getMessage(), e);
            return;
        }
        payoutService.applyWebhook(event);
    }

    private void recordUnparseable(String rawPayload, Exception cause) {
        String requestId = null;
        String eventType = null;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            requestId = root.path("requestId").asText(null);
            eventType = root.path("event_type").asText(null);
        } catch (Exception ignored) {
            // Payload isn't even valid JSON — nothing more to extract, fall through to logging.
        }

        log.error("Failed to process Nomba webhook (requestId={}, eventType={}): {}",
                requestId, eventType, cause.getMessage(), cause);

        if (requestId == null || requestId.isBlank()) {
            return; // Nothing to record against; already logged for investigation.
        }

        String finalRequestId = requestId;
        String finalEventType = eventType;
        NombaPaymentEvent event = paymentEventService.findByRequestId(requestId)
                .orElseGet(() -> paymentEventService.recordUnparseable(finalRequestId, finalEventType, rawPayload));
        paymentEventService.updateStatus(event.getId(), NombaPaymentEventStatus.FAILED,
                "Unprocessable payload: " + cause.getMessage());
    }
}
