package com.ojo.cyrus.controllers;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.exception.WebhookSignatureException;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.NombaWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/nomba")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Inbound provider webhooks. Authenticated by HMAC signature, not JWT/API key.")
@Slf4j
public class NombaWebhookController {
    private final NombaWebhookService webhookService;

    @Operation(summary = "Receive a Nomba webhook",
            description = "Verifies the HMAC signature, records the event, and attributes the payment to a customer. " +
                    "Returns 2xx once accepted; a non-2xx triggers Nomba's retry policy (exponential backoff, 5 retries). " +
                    "Internal idempotency and signature verification status ensure duplicate or invalid deliveries do not cause noise.")
    @PostMapping
    public CyrusApiResponse<Void> handle(@RequestHeader("nomba-signature") String signature,
                                         @RequestHeader("nomba-timestamp") String timestamp, @RequestBody String payload) {

        try {
            webhookService.handle(signature, timestamp, payload);
        } catch (WebhookSignatureException ex) {
            // We log signature failures but return 200 to stop the retry storm for what is likely
            // a misconfiguration or a malformed (but authentically intended) payload.
            log.error("Rejecting Nomba webhook due to signature mismatch, but returning 200 to satisfy provider retry policy: {}", ex.getMessage());
            return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook signature mismatch (ignored)", null);
        }

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook processed", null);
    }
}
