package com.ojo.cyrus.services;

import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.dto.NormalizedPayoutEvent;
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

        assertEquals("payment_failed", event.getEventType());
        assertEquals("7b28d6d1-f91e-46c3-b312-89e9XXXXXXX", event.getRequestId());
        assertEquals("POS-PURCHASE-71KD9-ae67-91fe-4b6a-a45b-689e9XXXXXXX", event.getProviderTransactionId());
        assertNull(event.getSessionId());
        assertNull(event.getVirtualAccountNumber());
        assertEquals(new BigInteger("2500000"), event.getAmount()); // 25000 * 100 = 2500000 kobo
        assertEquals(new BigInteger("15000"), event.getFee()); // 150 * 100 = 15000 kobo
        assertFalse(event.isVirtualAccountCredit());
    }

    @Test
    void testToPayoutEvent_Success() {
        // Real payout_success sample from Nomba's docs.
        String payload = """
                {
                  "event_type": "payout_success",
                  "requestId": "76a7df87-4819-493c-90ee-XXXXXXX",
                  "data": {
                    "merchant": {
                      "walletId": "693e907aad9ea59XXXXX",
                      "walletBalance": 420,
                      "userId": "613bb620-c8e5-45f6-9c00-XXXXXXXX"
                    },
                    "terminal": {},
                    "transaction": {
                      "fee": 20,
                      "sessionId": "09FG260206111644XXXXXX",
                      "type": "transfer",
                      "transactionId": "API-TRANSFER-057A0-21e353c0-4168-4275-8355-XXXXXX",
                      "responseCode": "",
                      "originatingFrom": "api",
                      "merchantTxRef": "20260212130PM",
                      "transactionAmount": 50,
                      "narration": "For API Test ",
                      "time": "2026-02-06T10:16:30Z"
                    },
                    "customer": {
                      "bankCode": "011",
                      "senderName": "Peter Okins",
                      "recipientName": "JOHN GRASS",
                      "bankName": "First Bank of Nigeria",
                      "accountNumber": "31107XXXX"
                    }
                  }
                }
                """;

        NormalizedPayoutEvent event = adapter.toPayoutEvent(payload);

        assertEquals("payout_success", event.eventType());
        assertEquals("76a7df87-4819-493c-90ee-XXXXXXX", event.requestId());
        assertEquals("20260212130PM", event.merchantTxRef()); // matches back to Payout.reference
        assertEquals("API-TRANSFER-057A0-21e353c0-4168-4275-8355-XXXXXX", event.providerTransactionId());
        assertEquals("09FG260206111644XXXXXX", event.sessionId());
        assertEquals(new BigInteger("2000"), event.feeKobo()); // 20 * 100
        assertEquals(new BigInteger("5000"), event.amountKobo()); // 50 * 100
        assertTrue(event.isSuccess());
        assertFalse(event.isFailureOrRefund());
    }

    @Test
    void testToPayoutEvent_Refund() {
        // Real payout_refund sample from Nomba's docs — funds returned to the wallet.
        String payload = """
                {
                    "event_type": "payout_refund",
                    "requestId": "062bbb0f-ecaa-481a-9ae5-12f73fXXXXXX",
                    "data": {
                        "merchant": { "walletId": "67khagklfXXXXXX", "walletBalance": 45000 },
                        "transaction": {
                            "fee": 7,
                            "sessionId": "090645251008183142932001fXXXXXX",
                            "type": "transfer",
                            "transactionId": "API-TRANSFER-9772C-bf28b3d1-e18f-4ecd-a33c-4fXXXXXX",
                            "merchantTxRef": "5TDL0CL7CP",
                            "transactionAmount": 45000,
                            "time": "2025-10-08T19:00:33Z"
                        }
                    }
                }
                """;

        NormalizedPayoutEvent event = adapter.toPayoutEvent(payload);

        assertEquals("payout_refund", event.eventType());
        assertEquals("5TDL0CL7CP", event.merchantTxRef());
        assertEquals(new BigInteger("4500000"), event.amountKobo()); // 45000 * 100
        assertFalse(event.isSuccess());
        assertTrue(event.isFailureOrRefund());
    }
}
