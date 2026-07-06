package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.exception.WebhookSignatureException;
import com.ojo.cyrus.models.dto.CyrusPaymentEvent;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.services.PaymentEventService;
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

        // 2. Normalize the raw provider payload into a Cyrus event.
        CyrusPaymentEvent event;
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
        //    delayed reconciliation requery (afterCommit) for any transaction it mints, so every
        //    ingestion path — webhook, admin replay — reconciles without the caller wiring it up.
        ingestionService.ingest(event, rawPayload);
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
        PaymentEvent event = paymentEventService.findByRequestId(requestId)
                .orElseGet(() -> paymentEventService.recordEvent(finalRequestId, Provider.NOMBA, finalEventType, rawPayload));
        paymentEventService.updateStatus(event.getId(), EventStatus.FAILED,
                "Unprocessable payload: " + cause.getMessage());
    }
}
