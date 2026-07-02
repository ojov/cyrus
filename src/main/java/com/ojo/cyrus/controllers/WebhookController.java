//package com.ojo.cyrus.controllers;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.ojo.cyrus.enums.ResponseCode;
//import com.ojo.cyrus.models.responses.CyrusApiResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
///**
// * Webhook Controller
// *
// * Handles incoming webhooks from Nomba.
// * Processes transfer notifications, deduplicates events, and triggers transaction reconciliation.
// *
// * **Critical for the payment flow:**
// * Nomba → sends webhook (transfer received)
// * → WebhookController receives it
// * → Deduplicates (don't process twice)
// * → Records transaction
// * → Async job matches to virtual account
// * → Emits merchant webhook (payment.received)
// */
//@Slf4j
//@RestController
//@RequestMapping("/v1/webhooks")
//@RequiredArgsConstructor
//public class WebhookController {
//
//    private final NombaClient nombaClient;
//    private final ObjectMapper objectMapper;
//
//    /**
//     * Receive webhooks from Nomba.
//     *
//     * **Webhook Flow:**
//     * 1. Nomba sends POST to /webhooks/nomba with transfer event
//     * 2. Verify signature (X-Nomba-Signature header)
//     * 3. Check if already processed (dedup by webhook_event_id)
//     * 4. Record PaymentEvent
//     * 5. Return 202 Accepted immediately (async processing)
//     * 6. Background job processes: find VA → create Transaction → emit merchant webhook
//     *
//     * **Example Nomba Webhook:**
//     * POST /v1/webhooks/nomba
//     * Headers:
//     *   X-Nomba-Signature: sha256=abc123...
//     *   Content-Type: application/json
//     * Body:
//     * {
//     *   "id": "nomba_evt_xyz",
//     *   "type": "transfer.received",
//     *   "timestamp": 1719902400,
//     *   "data": {
//     *     "account_number": "0123456789",
//     *     "amount": 50000,
//     *     "currency": "NGN",
//     *     "sender_name": "ACME CORP",
//     *     "sender_account": "0987654321",
//     *     "reference": "June Salary",
//     *     "transaction_id": "nomba_tx_abc123"
//     *   }
//     * }
//     */
//    @PostMapping("/nomba")
//    public ResponseEntity<CyrusApiResponse<String>> receiveNombaWebhook(
//            @RequestBody String rawPayload,
//            @RequestHeader(value = "X-Nomba-Signature", required = false) String signature,
//            @RequestHeader(value = "X-Nomba-Timestamp", required = false) String timestamp) {
//
//        try {
//            log.info("Received Nomba webhook");
//
//            // Step 1: Parse payload
//            JsonNode payload = objectMapper.readTree(rawPayload);
//            String webhookEventId = payload.get("id").asText();
//            String eventType = payload.get("type").asText();
//
//            log.debug("Webhook ID: {}, Type: {}", webhookEventId, eventType);
//
//            // Step 2: Verify Nomba signature
//            // TODO: Implement signature verification
//            // if (!nombaClient.verifyWebhookSignature(rawPayload, signature, nombaWebhookSecret)) {
//            //     log.warn("Invalid Nomba webhook signature");
//            //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
//            //         .body(CyrusApiResponse.error("Invalid signature"));
//            // }
//
//            // Step 3: Check for duplicates (dedup cache)
//            // TODO: Check WebhookDedupCache first
//            // Optional<PaymentEvent> existing = webhookService.checkAndRecordWebhook(merchantId, webhookEventId);
//            // if (existing.isPresent()) {
//            //     log.debug("Duplicate webhook. Already processed: {}", webhookEventId);
//            //     return ResponseEntity.accepted().body(
//            //         CyrusApiResponse.success("Duplicate webhook accepted"));
//            // }
//
//            // Step 4: Record the event
//            // TODO: Record PaymentEvent in DB
//            // webhookService.recordWebhookEvent(merchantId, webhookEventId, eventType, payload);
//
//            // Step 5: Queue async processing
//            // TODO: Queue background job to:
//            // - Find matching VirtualAccount (by account_number)
//            // - Create Transaction record (status=PENDING_MATCH)
//            // - Emit merchant webhook (payment.received)
//            // webhookService.queueProcessing(webhookEventId);
//
//            // Step 6: Return 202 Accepted immediately
//            // This tells Nomba the webhook was received successfully
//            // Actual processing happens asynchronously
//            log.info("Webhook {} accepted for processing", webhookEventId);
//
//            return ResponseEntity.accepted().body(
//                CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook accepted for processing", webhookEventId)
//            );
//
//        } catch (Exception e) {
//            log.error("Error processing Nomba webhook", e);
//            // Return 202 anyway (don't retry on our error)
//            // But log the error for investigation
//            return ResponseEntity.accepted().body(
//                CyrusApiResponse.failure(ResponseCode.INTERNAL_ERROR, "Internal error, webhook queued for retry")
//            );
//        }
//    }
//
//    /**
//     * Health check for webhook endpoint.
//     * Used to verify the webhook listener is running.
//     */
//    @GetMapping("/health")
//    public ResponseEntity<CyrusApiResponse<String>> webhookHealth() {
//        return ResponseEntity.ok(
//            CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook endpoint is healthy", "OK")
//        );
//    }
//
//    /**
//     * Webhook processing status (for ops/debugging).
//     * Shows pending, processing, and failed webhooks.
//     */
//    @GetMapping("/status")
//    public ResponseEntity<CyrusApiResponse<WebhookStatusResponse>> getWebhookStatus() {
//        try {
//            // TODO: Query database for webhook status
//            // - Count pending webhooks
//            // - Count being processed
//            // - Count failed
//            // - Show latest 10
//
//            WebhookStatusResponse status = new WebhookStatusResponse(
//                "operational",
//                0,
//                0,
//                0,
//                null
//            );
//
//            return ResponseEntity.ok(
//                CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook status retrieved", status)
//            );
//
//        } catch (Exception e) {
//            log.error("Error getting webhook status", e);
//            return ResponseEntity.internalServerError().body(
//                CyrusApiResponse.failure(ResponseCode.INTERNAL_ERROR, "Failed to get webhook status")
//            );
//        }
//    }
//
//    /**
//     * Manually replay a webhook (for debugging).
//     * If webhook processing failed, ops can retry.
//     */
//    @PostMapping("/replay/{webhookEventId}")
//    public ResponseEntity<CyrusApiResponse<String>> replayWebhook(
//            @PathVariable String webhookEventId) {
//
//        try {
//            log.info("Replaying webhook: {}", webhookEventId);
//
//            // TODO: Find PaymentEvent by webhookEventId
//            // TODO: Queue for reprocessing
//            // TODO: Update status
//
//            return ResponseEntity.accepted().body(
//                CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook queued for replay", webhookEventId)
//            );
//
//        } catch (Exception e) {
//            log.error("Error replaying webhook", e);
//            return ResponseEntity.internalServerError().body(
//                CyrusApiResponse.failure(ResponseCode.INTERNAL_ERROR, "Failed to replay webhook")
//            );
//        }
//    }
//}
//
//// ============ DTOs ============
//
//record WebhookStatusResponse(
//    String status,
//    Integer pendingCount,
//    Integer processingCount,
//    Integer failedCount,
//    String lastWebhookId
//) {}
