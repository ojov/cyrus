package com.ojo.cyrus.nomba;

import com.ojo.cyrus.config.properties.NombaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Verifies inbound Nomba webhook signatures.
 *
 * <p>Nomba signs a colon-delimited canonical string
 * ({@code event_type:requestId:userId:walletId:transactionId:type:time:responseCode:timestamp})
 * with HMAC-SHA256 keyed by the webhook secret, Base64-encoded, and sent in the
 * {@code nomba-signature} header. The comparison is constant-time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NombaSignatureService {

    private final NombaProperties nombaProperties;
    private final ObjectMapper objectMapper;

    public boolean isValid(String rawPayload, String signature, String timestampHeader) {
        if (signature == null || rawPayload == null) {
            return false;
        }
        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            JsonNode data = payload.path("data");
            JsonNode merchant = data.path("merchant");
            JsonNode tx = data.path("transaction");

            // Every signed field is extracted null-safely: missing / JSON-null / literal "null" -> ""
            // (mirrors Nomba's reference impl). Some payloads omit fields entirely — e.g. a POS
            // payment_failed has no merchant.walletId — so we must not NPE and reject a valid event.
            String canonical = String.join(":",
                    sigField(payload, "event_type"),
                    sigField(payload, "requestId"),
                    sigField(merchant, "userId"),
                    sigField(merchant, "walletId"),
                    sigField(tx, "transactionId"),
                    sigField(tx, "type"),
                    sigField(tx, "time"),
                    sigField(tx, "responseCode"),
                    timestampHeader == null ? "" : timestampHeader
            );

            String computed = hmacBase64(canonical, nombaProperties.webhookSecret());

            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("Nomba webhook signature verification failed to evaluate: {}", e.getMessage());
            return false;
        }
    }

    /** Signed-field extraction matching Nomba's reference: missing / null / literal "null" -> "". */
    private static String sigField(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        String text = value.asText();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private String hmacBase64(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }
}
