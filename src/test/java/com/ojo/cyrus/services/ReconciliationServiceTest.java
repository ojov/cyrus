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
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private FeeProperties feeProperties;

    private ReconciliationService service;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        service = new ReconciliationService(
                nombaTransactionClient, transactionRepository, transactionManager,
                reconciliationProperties, merchantWebhookService, ledgerService, feeProperties
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
                .amount(new BigInteger("250000"))
                .fee(new BigInteger("1000"))
                .sessionId("session-1")
                .providerTransactionId("API-VACT-TEST-1")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-1")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(feeProperties.inflowPercent()).thenReturn(new BigDecimal("1.5"));
        when(feeProperties.inflowMinKobo()).thenReturn(new BigInteger("1500"));
        when(feeProperties.inflowMaxKobo()).thenReturn(new BigInteger("22500"));

        var providerTx = new NombaTransactionData(
                "API-VACT-TEST-1", "session-1", "2500.0", "NGN", "SUCCESSFUL"
        );
        when(nombaTransactionClient.requeryTransaction("session-1")).thenReturn(providerTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.MATCHED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        assertThat(tx.getMatchStatusDetails()).isNull();

        verify(ledgerService).credit(eq(merchant), eq(new BigInteger("250000")), eq(tx), any(), any());
        verify(ledgerService, times(2)).debit(eq(merchant), any(), eq(tx), any(), any());
        verify(merchantWebhookService).recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_SUCCEEDED));
    }

    @Test
    void discrepancyOnAmount() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigInteger("250000"))
                .fee(new BigInteger("1000"))
                .sessionId("session-2")
                .providerTransactionId("API-VACT-TEST-2")
                .status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference("ref-2")
                .build();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(feeProperties.inflowPercent()).thenReturn(new BigDecimal("1.5"));
        when(feeProperties.inflowMinKobo()).thenReturn(new BigInteger("1500"));
        when(feeProperties.inflowMaxKobo()).thenReturn(new BigInteger("22500"));

        var providerTx = new NombaTransactionData(
                "API-VACT-TEST-2", "session-2", "2400.0", "NGN", "SUCCESSFUL"
        );
        when(nombaTransactionClient.requeryTransaction("session-2")).thenReturn(providerTx);

        var outcome = service.reconcileTransactionById(txId);

        assertThat(outcome).isEqualTo(ReconciliationOutcome.DISCREPANCY);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.DISCREPANCY);
        assertThat(tx.getMatchStatusDetails()).contains("Webhook amount=250000", "Nomba requery amount=240000");

        verify(ledgerService).credit(any(), any(), any(), any(), any());
        verify(merchantWebhookService).recordAndScheduleDispatch(eq(tx), eq(MerchantWebhookEventType.PAYMENT_SUCCEEDED));
    }

    @Test
    void noSessionId_marksManualReview() {
        var txId = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(txId)
                .merchant(merchant)
                .amount(new BigInteger("250000"))
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
                .amount(new BigInteger("250000"))
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
                .amount(new BigInteger("250000"))
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
