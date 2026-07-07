package com.ojo.cyrus.nomba;

import com.ojo.cyrus.exception.NombaIntegrationException;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Maps a raw Nomba webhook payload into a provider-agnostic {@link NormalizedPaymentEvent}. Keeps all
 * Nomba-specific JSON shape knowledge in one place so the rest of the pipeline never sees raw
 * provider payloads.
 */
@Component
@RequiredArgsConstructor
public class NombaWebhookAdapter {

    private final ObjectMapper objectMapper;
    public static final String DEFAULT_CURRENCY = "NGN";

    public NormalizedPaymentEvent toCyrusEvent(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode data = root.path("data");
            JsonNode tx = data.path("transaction");
            JsonNode customer = data.path("customer");
            JsonNode merchant = data.path("merchant");

            return NormalizedPaymentEvent.builder()
                    .eventType(text(root, "event_type"))
                    .requestId(text(root, "requestId"))
                    .providerTransactionId(text(tx, "transactionId"))
                    .sessionId(text(tx, "sessionId"))
                    // Some payloads omit this (see NombaSignatureService) — resolution falls back
                    // to VA-based merchant attribution when null.
                    .walletId(text(merchant, "walletId"))
                    // The credited virtual account: a VA transfer carries the VA's NUBAN as
                    // `aliasAccountNumber` (with aliasAccountType "VIRTUAL"). Non-VA events (e.g. POS
                    // purchases) omit it → null → gated out in ingestion.
                    .virtualAccountNumber(text(tx, "aliasAccountNumber"))
                    .amount(toKobo(tx.path("transactionAmount")))            // Nomba sends naira → store kobo
                    .fee(toKobo(tx.path("fee")))
                    .currency(tx.hasNonNull("currency") ? tx.get("currency").asString() : DEFAULT_CURRENCY)
                    .eventTime(parseTime(text(tx, "time")))
                    .payer(NormalizedPaymentEvent.Payer.builder()
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
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    /** Nomba reports amounts in naira; Cyrus stores integer kobo via {@link NombaCurrencyUtil#nairaToKobo(String)}. */
    public static BigInteger toKobo(JsonNode amountNode) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return BigInteger.ZERO;
        }
        // Extract the string value from the JsonNode (handles both number and string representations).
        String nairaStr = amountNode.isNumber() ? amountNode.decimalValue().toPlainString() : amountNode.asString();
        return NombaCurrencyUtil.nairaToKobo(nairaStr);
    }

    private static Instant parseTime(String time) {
        return time == null ? Instant.now() : OffsetDateTime.parse(time).toInstant();
    }
}
