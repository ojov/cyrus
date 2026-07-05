package com.ojo.cyrus.controllers;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.NombaWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/nomba")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Inbound provider webhooks. Authenticated by HMAC signature, not JWT/API key.")
public class NombaWebhookController {
    private final NombaWebhookService webhookService;

    @Operation(summary = "Receive a Nomba webhook",
            description = "Verifies the HMAC signature, records the event, and attributes the payment to a customer. " +
                    "Returns 2xx once accepted; a non-2xx triggers Nomba's retry policy (exponential backoff, 5 retries). " +
                    "Internal idempotency and signature verification status ensure duplicate or invalid deliveries do not cause noise.")
    @PostMapping
    public CyrusApiResponse<Void> handle(@RequestHeader("nomba-signature") String signature,
                                         @RequestHeader("nomba-timestamp") String timestamp, @RequestBody String payload) {

        webhookService.handle(signature, timestamp, payload);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook processed", null);
    }
}
