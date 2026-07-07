package com.ojo.cyrus.services;

import com.ojo.cyrus.models.dto.DeliveryOutcome;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.UUID;

/**
 * Signs and sends a single outbound webhook POST via {@code merchantWebhookRestClient}, and
 * classifies the result into a {@link DeliveryOutcome}. Pure transport — no DB access, no retry
 * decision-making; that orchestration belongs to {@link MerchantWebhookDispatcher}.
 *
 * <p>The signature covers {@code timestamp + "." + payload} (Stripe-style), not the payload alone —
 * binding the timestamp into the signed content is what makes {@code X-Cyrus-Timestamp} meaningful
 * for replay protection. A signature computed over the payload alone would let a captured
 * (payload, signature) pair be replayed indefinitely with a freshly-forged timestamp, since the
 * timestamp wouldn't be part of what the signature actually attests to.
 */
@Component
@RequiredArgsConstructor
public class MerchantWebhookClient {

    private final RestClient merchantWebhookRestClient;

    public DeliveryOutcome send(String url, String eventType, UUID deliveryId, String payload, String secret) {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        String signature = "sha256=" + CryptoUtil.hmacSha256(timestamp + "." + payload, secret);
        try {
            ResponseEntity<Void> response = merchantWebhookRestClient.post()
                    .uri(url)
                    .header("X-Cyrus-Event", eventType)
                    .header("X-Cyrus-Delivery", deliveryId.toString())
                    .header("X-Cyrus-Timestamp", timestamp)
                    .header("X-Cyrus-Signature", signature)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            int code = response.getStatusCode().value();
            if (response.getStatusCode().is2xxSuccessful()) {
                return DeliveryOutcome.success(code);
            }
            // Reached only for 1xx/3xx (4xx/5xx throw below, and RestClient doesn't follow
            // redirects) — the endpoint isn't behaving like a webhook receiver; not worth retrying.
            return DeliveryOutcome.permanentFailure(code, "Non-2xx response: " + code);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            // 429 (rate limited) and any 5xx are transient — retry. Other 4xx (400/401/403/404/410...)
            // mean the request itself is rejected and retrying the identical payload will fail the
            // same way every time, so those fail immediately instead of burning the retry schedule.
            boolean retryable = code == 429 || e.getStatusCode().is5xxServerError();
            return retryable
                    ? DeliveryOutcome.retryableFailure(code, e.getStatusText())
                    : DeliveryOutcome.permanentFailure(code, e.getStatusText());
        } catch (ResourceAccessException e) {
            // Connection refused / timeout / DNS / connection reset — transient network failure.
            return DeliveryOutcome.retryableFailure(null, "Connection failure: " + e.getMessage());
        } catch (Exception e) {
            return DeliveryOutcome.retryableFailure(null, "Delivery error: " + e.getMessage());
        }
    }
}
