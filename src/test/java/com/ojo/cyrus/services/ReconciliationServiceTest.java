package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.FeeProperties;
import com.ojo.cyrus.config.properties.ReconciliationProperties;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantWebhookEventType;
import com.ojo.cyrus.enums.ReconciliationOutcome;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.nomba.clients.NombaTransactionClient;
import com.ojo.cyrus.nomba.dto.NombaTransactionData;
import com.ojo.cyrus.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private NombaTransactionClient nombaTransactionClient;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ReconciliationProperties reconciliationProperties;
    @Mock
    private MerchantWebhookService merchantWebhookService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private FeeConfigService feeConfigService;
    @Mock
    private PlatformProfitService platformProfitService;

    private FeeProperties feeProperties;

    private ReconciliationService service;
    private Merchant merchant;

    /**
     * Mockito matcher for money: matches by {@code compareTo}, not {@code equals} —
     * BigDecimal.equals is scale-sensitive ({@code eq(new BigDecimal("12000"))} would NOT match a
     * canonical scale-4 {@code 12000.0000}).
     */
    private static BigDecimal koboEq(String expected) {
        BigDecimal want = new BigDecimal(expected);
        return argThat(actual -> actual != null && actual.compareTo(want) == 0);
    }

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        feeProperties = new FeeProperties();
        feeProperties.setInflowPercent(new BigDecimal("1.5"));
        feeProperties.setInflowMinKobo(new BigDecimal("1500"));
        feeProperties.setInflowMaxKobo(new BigDecimal("22500"));
        lenient().when(feeConfigService.current()).thenReturn(feeProperties);

        service = new ReconciliationService(
                nombaTransactionClient, transactionRepository, transactionManager,
                reconciliationProperties, merchantWebhookService, ledgerService, feeConfigService,
                platformProfitService
        );

        merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .businessName("Test Merchant")
                .businessEmail("test@example.com")
                .passwordHash("hash")
                .build();
    }

    @Test
    void matched() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigDecimal("250000"))
                .fee(new BigDecimal("1000"))
                .sessionId("session-1")
                .providerTransactionId("API-VACT-TEST-1")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-1")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        var providerTx = new NombaTransactionData(
                "API-VACT-TEST-1", "session-1", "2500.0", "NGN", "SUCCESSFUL"
        );
        when(nombaTransactionClient.requeryTransaction("session-1")).thenReturn(providerTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.MATCHED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(tx.getMatchStatusDetails()).isNull();

        verify(ledgerService).credit(eq(merchant), koboEq("250000"), eq(tx), any(), any());
        verify(ledgerService, times(2)).debit(eq(merchant), any(), eq(tx), any(), any());
        verify(merchantWebhookService).recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_SUCCEEDED));

        // Profit ledger: inflow (250000) + fee accrual (merchantFee=3750 minus Nomba fee=1000 = 2750).
        verify(platformProfitService).recordInflow(eq(tx));
        verify(platformProfitService).recordFeeAccrual(eq(tx), koboEq("2750"));
    }

    @Test
    void discrepancyOnAmount() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigDecimal("250000"))
                .fee(new BigDecimal("1000"))
                .sessionId("session-2")
                .providerTransactionId("API-VACT-TEST-2")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-2")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        var providerTx = new NombaTransactionData(
                "API-VACT-TEST-2", "session-2", "2400.0", "NGN", "SUCCESSFUL"
        );
        when(nombaTransactionClient.requeryTransaction("session-2")).thenReturn(providerTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.DISCREPANCY);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.DISCREPANCY);
        assertThat(tx.getMatchStatusDetails()).contains("Webhook amount=250000", "Nomba requery amount=240000");
        // The transaction amount is corrected to Nomba's confirmed value.
        assertThat(tx.getAmount()).isEqualByComparingTo("240000");

        verify(ledgerService).credit(eq(merchant), koboEq("240000"), eq(tx), any(), any());
        verify(merchantWebhookService).recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_SUCCEEDED));

        // Profit ledger: inflow (240000) + fee accrual (merchantFee=3600 minus Nomba fee=1000 = 2600).
        verify(platformProfitService).recordInflow(eq(tx));
        verify(platformProfitService).recordFeeAccrual(eq(tx), koboEq("2600"));
    }

    @Test
    void fractionalKoboFromNombaIsPreserved() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                // Ingested from a webhook reporting ₦100.005 → 10000.5 kobo (fractional, preserved).
                .amount(new BigDecimal("10000.5000"))
                .fee(new BigDecimal("10"))
                .sessionId("session-frac")
                .providerTransactionId("API-VACT-TEST-FRAC")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-frac")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        // Requery confirms the same fractional naira amount — scales match via compareTo, so this
        // is a MATCH, not a false DISCREPANCY (10000.5 vs 10000.5000 must compare equal).
        var providerTx = new NombaTransactionData(
                "API-VACT-TEST-FRAC", "session-frac", "100.005", "NGN", "SUCCESSFUL"
        );
        when(nombaTransactionClient.requeryTransaction("session-frac")).thenReturn(providerTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.MATCHED);
        // The full fractional gross is credited...
        verify(ledgerService).credit(eq(merchant), koboEq("10000.5"), eq(tx), any(), any());
        // ...and the merchant fee keeps its own sub-kobo precision: 1.5% of 10000.5 = 150.0075,
        // below the 1500 floor → clamped to 1500 (whole), but merchantFeeKobo is set from the
        // clamped value. Assert what was persisted on the transaction.
        assertThat(tx.getMerchantFeeKobo()).isEqualByComparingTo("1500");

        // Profit ledger: inflow (10000.5) + fee accrual (merchantFee=1500 minus Nomba fee=10 = 1490).
        verify(platformProfitService).recordInflow(eq(tx));
        verify(platformProfitService).recordFeeAccrual(eq(tx), koboEq("1490"));
    }

    @Test
    void noSessionId_marksManualReview() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigDecimal("250000"))
                .sessionId(null)
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-3")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.NOT_FOUND);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MANUAL_REVIEW);

        verify(merchantWebhookService)
                .recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_FLAGGED));
        verifyNoInteractions(nombaTransactionClient);
    }

    @Test
    void notFoundByNomba_incrementsAttempts() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigDecimal("250000"))
                .sessionId("session-3")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-4")
                .reconciliationAttempts(0)
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(reconciliationProperties.maxAttempts()).thenReturn(5);

        var notFoundTx = new NombaTransactionData(null, null, null, null, null);
        when(nombaTransactionClient.requeryTransaction("session-3")).thenReturn(notFoundTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.NOT_FOUND);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getReconciliationAttempts()).isEqualTo(1);

        verifyNoInteractions(ledgerService);
        verifyNoInteractions(merchantWebhookService);
    }

    @Test
    void notFoundExceedsMaxAttempts_marksManualReview() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigDecimal("250000"))
                .sessionId("session-4")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-5")
                .reconciliationAttempts(4)
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(reconciliationProperties.maxAttempts()).thenReturn(5);

        var notFoundTx = new NombaTransactionData(null, null, null, null, null);
        when(nombaTransactionClient.requeryTransaction("session-4")).thenReturn(notFoundTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.NOT_FOUND);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MANUAL_REVIEW);

        verify(merchantWebhookService)
                .recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_FLAGGED));
    }
}
