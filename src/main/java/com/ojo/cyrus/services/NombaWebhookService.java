package com.ojo.cyrus.services;

import com.ojo.cyrus.exception.WebhookSignatureException;
import com.ojo.cyrus.models.dto.CyrusPaymentEvent;
import com.ojo.cyrus.nomba.NombaSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public void handle(String signature, String timestamp, String rawPayload) {
        // 1. Verify first — reject anything that isn't authentically from Nomba.
        if (!signatureService.isValid(rawPayload, signature, timestamp)) {
            throw new WebhookSignatureException("Invalid Nomba webhook signature");
        }

        // 2. Normalize the raw provider payload into a Cyrus event.
        CyrusPaymentEvent event = adapter.toCyrusEvent(rawPayload);

        // 3. Record + attribute (idempotent, transactional).
        ingestionService.ingest(event, rawPayload);
    }
}
