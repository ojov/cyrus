package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.nomba.NombaWebhookAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class NombaWebhookAdapterTest {

    private NombaWebhookAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new NombaWebhookAdapter(objectMapper);
    }

    @Test
    void testToCyrusEvent_Success() {
        String payload = """
                {
                  "event_type": "payment_success",
                  "requestId": "49e11b44-909b-4f83-82b4-9a83aXXXXXX",
                  "data": {
                    "merchant": {
                      "walletId": "693e907aad9ea59616XXXX",
                      "walletBalance": 539.4,
                      "userId": "613bb620-c8e5-45f6-9c00-XXXXXXXX"
                    },
                    "terminal": {},
                    "transaction": {
                      "aliasAccountNumber": "967913XXX",
                      "fee": 0.6,
                      "sessionId": "1000042602061021531516XXXXXX",
                      "type": "vact_transfer",
                      "transactionId": "API-VACT_TRA-613BB-eeae578a-cdd4-459c-8bd5-XXXXXX",
                      "aliasAccountName": "Peter/Peter Enterprise",
                      "responseCode": "",
                      "originatingFrom": "api",
                      "transactionAmount": 120,
                      "narration": "Transfer from JOHN GRASS",
                      "time": "2026-02-06T10:21:56Z",
                      "aliasAccountReference": "122320250916PM",
                      "aliasAccountType": "VIRTUAL"
                    },
                    "customer": {
                      "bankCode": "305",
                      "senderName": "JOHN GRASS",
                      "bankName": "Paycom (Opay)",
                      "accountNumber": "81689XXX"
                    }
                  }
                }
                """;

        NormalizedPaymentEvent event = adapter.toCyrusEvent(payload);

        assertEquals(Provider.NOMBA, event.getProvider());
        assertEquals("payment_success", event.getEventType());
        assertEquals("49e11b44-909b-4f83-82b4-9a83aXXXXXX", event.getRequestId());
        assertEquals("API-VACT_TRA-613BB-eeae578a-cdd4-459c-8bd5-XXXXXX", event.getProviderTransactionId());
        assertEquals("1000042602061021531516XXXXXX", event.getSessionId());
        assertEquals("967913XXX", event.getVirtualAccountNumber());
        assertEquals(new BigInteger("12000"), event.getAmount()); // 120 * 100 = 12000 kobo
        assertEquals(new BigInteger("60"), event.getFee()); // 0.6 * 100 = 60 kobo
        assertEquals("NGN", event.getCurrency());
        assertNotNull(event.getPayer());
        assertEquals("JOHN GRASS", event.getPayer().getName());
        assertEquals("81689XXX", event.getPayer().getAccountNumber());
        assertEquals("305", event.getPayer().getBankCode());
        assertEquals("Paycom (Opay)", event.getPayer().getBankName());
        assertTrue(event.isVirtualAccountCredit());
    }

    @Test
    void testToCyrusEvent_Failure() {
        String payload = """
                {
                    "event_type": "payment_failed",
                    "requestId": "7b28d6d1-f91e-46c3-b312-89e9XXXXXXX",
                    "data": {
                        "merchant": {
                            "userId": "usr_71kd89e9XXXXXXX"
                        },
                        "terminal": {
                            "terminalLabel": "IKEJA MALL",
                            "terminalId": "3PLQXXX"
                        },
                        "transaction": {
                            "fee": 150,
                            "type": "purchase",
                            "transactionId": "POS-PURCHASE-71KD9-ae67-91fe-4b6a-a45b-689e9XXXXXXX",
                            "responseCodeMessage": "Insufficient Funds",
                            "rrn": "2510089e9XXXXXXX5",
                            "cardIssuer": "MASTERCARD",
                            "responseCode": "51",
                            "originatingFrom": "pos",
                            "terminalSerialNumber": "91230989e9XXXXXXX",
                            "cardBank": "058",
                            "transactionAmount": 25000,
                            "time": "2025-10-06T17:38:45Z"
                        },
                        "customer": {
                            "productId": "058",
                            "cardPan": "539983 **** **** 4297"
                        }
                    }
                }
                """;

        NormalizedPaymentEvent event = adapter.toCyrusEvent(payload);

        assertEquals(Provider.NOMBA, event.getProvider());
        assertEquals("payment_failed", event.getEventType());
        assertEquals("7b28d6d1-f91e-46c3-b312-89e9XXXXXXX", event.getRequestId());
        assertEquals("POS-PURCHASE-71KD9-ae67-91fe-4b6a-a45b-689e9XXXXXXX", event.getProviderTransactionId());
        assertNull(event.getSessionId());
        assertNull(event.getVirtualAccountNumber());
        assertEquals(new BigInteger("2500000"), event.getAmount()); // 25000 * 100 = 2500000 kobo
        assertEquals(new BigInteger("15000"), event.getFee()); // 150 * 100 = 15000 kobo
        assertFalse(event.isVirtualAccountCredit());
    }
}
