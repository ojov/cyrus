package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.MissingWebhookSweepProperties;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.nomba.clients.NombaTransactionClient;
import com.ojo.cyrus.nomba.dto.NombaSubAccountTransactionPage;
import com.ojo.cyrus.repositories.NombaPaymentEventRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MissingWebhookSweepService}. Fixtures are the real Nomba sub-account list
 * shape (a {@code vact_transfer} VA credit and a {@code transfer} payout), verified against a live
 * response + its paired webhook — so these also pin the field mapping the sweep depends on.
 */
@ExtendWith(MockitoExtension.class)
class MissingWebhookSweepServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // The real ₦100 VA credit (webhook was lost) — id, sessionId, recipientAccountNumber and
    // virtualAccountReference are the exact values Nomba returned, matching the paired webhook.
    private static final String VA_CREDIT = """
            {
              "id":"API-VACT_TRA-9761F-e21ec899-408f-4140-a478-1f4d4dac04c6",
              "status":"SUCCESS",
              "amount":"100.0",
              "fixedCharge":"10.0",
              "type":"vact_transfer",
              "timeCreated":"2026-07-14T13:32:51.297Z",
              "recipientAccountNumber":"7252606275",
              "virtualAccountReference":"otici_cust_12",
              "recipientAccountType":"VIRTUAL",
              "senderName":"OSAMUDIAMEN VICTOR OJO",
              "ktaSenderAccountNumber":"8146169103",
              "bankCode":"305",
              "bankName":"Paycom (Opay)",
              "currency":"NGN",
              "entryType":"CREDIT",
              "sessionId":"100004260714133247165331497441",
              "narration":"reconciliation"
            }""";

    private static final String CREDIT_ID = "API-VACT_TRA-9761F-e21ec899-408f-4140-a478-1f4d4dac04c6";
    private static final String CREDIT_SESSION = "100004260714133247165331497441";

    // An outbound payout leg — entryType DEBIT, no virtualAccountReference. Must be ignored.
    private static final String PAYOUT = """
            {
              "id":"API-TRANSFER-08088-8aada526",
              "status":"SUCCESS",
              "amount":"75.0",
              "type":"transfer",
              "entryType":"DEBIT",
              "merchantTxRef":"pyt_b46f35d0cacf48f6940dfd7dd26e35fd"
            }""";

    @Mock
    private NombaTransactionClient nombaTransactionClient;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private NombaPaymentEventRepository paymentEventRepository;
    @Mock
    private TransactionIngestionService ingestionService;

    private MissingWebhookSweepService service(boolean dryRun) {
        var props = new MissingWebhookSweepProperties(21600L, 48L, dryRun, false);
        return new MissingWebhookSweepService(
                nombaTransactionClient, transactionRepository, paymentEventRepository, ingestionService, props);
    }

    private static JsonNode item(String json) {
        return MAPPER.readTree(json);
    }

    private void returnsPage(JsonNode... items) {
        when(nombaTransactionClient.listSubAccountTransactions(any(), any(), anyInt(), isNull()))
                .thenReturn(new NombaSubAccountTransactionPage(List.of(items), ""));
    }

    @Test
    void successfulVaCreditWithNoLocalRecord_isReplayedThroughIngestionWithMappedFields() {
        returnsPage(item(VA_CREDIT));
        when(transactionRepository.existsByProviderTransactionId(CREDIT_ID)).thenReturn(false);
        when(transactionRepository.existsBySessionId(CREDIT_SESSION)).thenReturn(false);
        when(paymentEventRepository.existsByRequestId("sweep:" + CREDIT_ID)).thenReturn(false);

        service(false).sweepMissingWebhooks();

        var captor = ArgumentCaptor.forClass(NormalizedPaymentEvent.class);
        verify(ingestionService).ingest(captor.capture(), anyString());

        var event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("payment_success");
        assertThat(event.getRequestId()).isEqualTo("sweep:" + CREDIT_ID);
        assertThat(event.getProviderTransactionId()).isEqualTo(CREDIT_ID);
        assertThat(event.getSessionId()).isEqualTo(CREDIT_SESSION);
        assertThat(event.getVirtualAccountNumber()).isEqualTo("7252606275");
        assertThat(event.getCurrency()).isEqualTo("NGN");
        // ₦100 → 10000 kobo, ₦10 fixedCharge → 1000 kobo (naira decimal string → scale-4 kobo).
        assertThat(event.getAmount()).isEqualByComparingTo("10000");
        assertThat(event.getFee()).isEqualByComparingTo("1000");
        assertThat(event.getPayer().getName()).isEqualTo("OSAMUDIAMEN VICTOR OJO");
        assertThat(event.getPayer().getAccountNumber()).isEqualTo("8146169103");
        assertThat(event.getPayer().getBankCode()).isEqualTo("305");
        assertThat(event.getPayer().getBankName()).isEqualTo("Paycom (Opay)");
    }

    @Test
    void nonSuccessCreditLeg_isSkipped_neverIngestedOrCredited() {
        // A REVERSED_BY_VENDOR credit leg still carries entryType CREDIT + a virtualAccountReference,
        // but must NOT be recovered — reconciliation would otherwise promote+credit it. This is the
        // status-filter guard that the sweep adds over the webhook path.
        String reversed = VA_CREDIT.replace("\"status\":\"SUCCESS\"", "\"status\":\"REVERSED_BY_VENDOR\"");
        returnsPage(item(reversed));

        service(false).sweepMissingWebhooks();

        verifyNoInteractions(ingestionService);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(paymentEventRepository);
    }

    @Test
    void outboundPayoutLeg_isSkipped() {
        returnsPage(item(PAYOUT));

        service(false).sweepMissingWebhooks();

        verifyNoInteractions(ingestionService);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(paymentEventRepository);
    }

    @Test
    void alreadyIngestedByProviderId_isSkipped() {
        returnsPage(item(VA_CREDIT));
        when(transactionRepository.existsByProviderTransactionId(CREDIT_ID)).thenReturn(true);

        service(false).sweepMissingWebhooks();

        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void alreadyIngestedBySessionId_isSkipped() {
        // The list id doesn't match, but the sessionId does (the reliable second dedup key).
        returnsPage(item(VA_CREDIT));
        when(transactionRepository.existsByProviderTransactionId(CREDIT_ID)).thenReturn(false);
        when(transactionRepository.existsBySessionId(CREDIT_SESSION)).thenReturn(true);

        service(false).sweepMissingWebhooks();

        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void dryRun_detectsGapButNeverIngests() {
        returnsPage(item(VA_CREDIT));
        when(transactionRepository.existsByProviderTransactionId(CREDIT_ID)).thenReturn(false);
        when(transactionRepository.existsBySessionId(CREDIT_SESSION)).thenReturn(false);
        when(paymentEventRepository.existsByRequestId("sweep:" + CREDIT_ID)).thenReturn(false);

        service(true).sweepMissingWebhooks();

        // dry-run writes nothing — the whole point of the safe default.
        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void stopsAtMaxPagesWhenCursorNeverTerminates() {
        // Every page returns a non-empty cursor (a runaway/non-advancing cursor). The item is a
        // payout so processItem returns early without touching the repos — this isolates the loop's
        // MAX_PAGES safety bound. any() matches the null first cursor and the "next" cursor alike.
        when(nombaTransactionClient.listSubAccountTransactions(any(), any(), anyInt(), any()))
                .thenReturn(new NombaSubAccountTransactionPage(List.of(item(PAYOUT)), "next"));

        service(true).sweepMissingWebhooks();

        // MAX_PAGES is 50 in the service — the loop must stop there, not spin forever.
        verify(nombaTransactionClient, times(50)).listSubAccountTransactions(any(), any(), anyInt(), any());
    }

    @Test
    void nullResultsList_doesNotThrow() {
        // An edge response whose data omits `results` (requireData only guarantees data != null).
        when(nombaTransactionClient.listSubAccountTransactions(any(), any(), anyInt(), isNull()))
                .thenReturn(new NombaSubAccountTransactionPage(null, ""));

        assertThatCode(() -> service(false).sweepMissingWebhooks()).doesNotThrowAnyException();
        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void emptyWindow_detectsNoGaps() {
        returnsPage(); // results = [] (empty), cursor "" — nothing in the trailing window

        service(false).sweepMissingWebhooks();

        verify(ingestionService, never()).ingest(any(), anyString());
    }

    @Test
    void paginatesUntilCursorExhausted() {
        var itemA = item(VA_CREDIT);
        var itemB = item(VA_CREDIT
                .replace(CREDIT_ID, "API-VACT_TRA-PAGE2-xxxx")
                .replace(CREDIT_SESSION, "SESSION-PAGE2"));

        // Page 1 returns a non-empty cursor; page 2 (fetched with that cursor) returns "" to stop.
        when(nombaTransactionClient.listSubAccountTransactions(any(), any(), anyInt(), isNull()))
                .thenReturn(new NombaSubAccountTransactionPage(List.of(itemA), "cursor1"));
        when(nombaTransactionClient.listSubAccountTransactions(any(), any(), anyInt(), eq("cursor1")))
                .thenReturn(new NombaSubAccountTransactionPage(List.of(itemB), ""));

        when(transactionRepository.existsByProviderTransactionId(anyString())).thenReturn(false);
        when(transactionRepository.existsBySessionId(anyString())).thenReturn(false);
        when(paymentEventRepository.existsByRequestId(anyString())).thenReturn(false);

        service(false).sweepMissingWebhooks();

        // Both pages were fetched and both gaps recovered.
        verify(nombaTransactionClient).listSubAccountTransactions(any(), any(), anyInt(), isNull());
        verify(nombaTransactionClient).listSubAccountTransactions(any(), any(), anyInt(), eq("cursor1"));
        verify(ingestionService, times(2)).ingest(any(), anyString());
    }
}
