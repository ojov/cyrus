package com.ojo.cyrus.nomba;

import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.models.dto.CyrusPaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Maps a raw Nomba webhook payload into a provider-agnostic {@link CyrusPaymentEvent}. Keeps all
 * Nomba-specific JSON shape knowledge in one place so the rest of the pipeline never sees raw
 * provider payloads.
 */
@Component
@RequiredArgsConstructor
public class NombaWebhookAdapter {

    private final ObjectMapper objectMapper;

    public CyrusPaymentEvent toCyrusEvent(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode data = root.path("data");
            JsonNode tx = data.path("transaction");
            JsonNode customer = data.path("customer");

            return CyrusPaymentEvent.builder()
                    .provider(Provider.NOMBA)
                    .eventType(text(root, "event_type"))
                    .requestId(text(root, "requestId"))
                    .providerTransactionId(text(tx, "transactionId"))
                    .sessionId(text(tx, "sessionId"))
                    // The credited virtual account: a VA transfer carries the VA's NUBAN as
                    // `aliasAccountNumber` (with aliasAccountType "VIRTUAL"). Non-VA events (e.g. POS
                    // purchases) omit it → null → gated out in ingestion.
                    .virtualAccountNumber(text(tx, "aliasAccountNumber"))
                    .amount(toKobo(tx.path("transactionAmount")))            // Nomba sends naira → store kobo
                    .fee(toKobo(tx.path("fee")))
                    .currency(tx.hasNonNull("currency") ? tx.get("currency").asText() : "NGN")
                    .eventTime(parseTime(text(tx, "time")))
                    .payer(CyrusPaymentEvent.Payer.builder()
                            .name(text(customer, "senderName"))
                            .accountNumber(text(customer, "accountNumber"))
                            .bankCode(text(customer, "bankCode"))
                            .bankName(text(customer, "bankName"))
                            .build())
                    .build();
        } catch (NombaIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new NombaIntegrationException("Failed to parse Nomba webhook payload: " + e.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** Nomba reports amounts in naira; Cyrus stores integer kobo (see the money convention). */
    private static BigInteger toKobo(JsonNode amountNode) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return BigInteger.ZERO;
        }
        BigDecimal naira = amountNode.isNumber()
                ? amountNode.decimalValue()
                : new BigDecimal(amountNode.asText().trim());
        return naira.movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).toBigIntegerExact();
    }

    private static Instant parseTime(String time) {
        return time == null ? Instant.now() : OffsetDateTime.parse(time).toInstant();
    }
}
