package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.FeeProperties;
import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.dto.NormalizedPayoutEvent;
import com.ojo.cyrus.models.entities.Beneficiary;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Payout;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.requests.CreatePayoutRequest;
import com.ojo.cyrus.models.responses.PayoutResponse;
import com.ojo.cyrus.nomba.clients.NombaTransferClient;
import com.ojo.cyrus.nomba.dto.NombaBankTransferData;
import com.ojo.cyrus.repositories.BeneficiaryRepository;
import com.ojo.cyrus.repositories.PayoutRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Payouts are finalized from three independent paths that can race each other: the synchronous
 * {@code initiate()} response, the async {@code payout_success}/{@code payout_failed} webhook, and
 * the periodic requery sweep. Each path pessimistic-locks the {@link Payout} row and re-checks its
 * status before mutating it, specifically so a duplicate/late signal finds the payout already
 * terminal and becomes a no-op instead of double-crediting or double-refunding the wallet. These
 * tests simulate that race by mutating the "persisted" payout mid-call (inside a mocked answer),
 * standing in for a concurrent webhook/sweep having already won.
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private BeneficiaryRepository beneficiaryRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MerchantService merchantService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private NombaTransferClient nombaTransferClient;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private FeeConfigService feeConfigService;
    @Mock
    private PlatformProfitService platformProfitService;

    private PayoutService service;
    private Merchant merchant;
    private Beneficiary beneficiary;

    /** Same convention as ReconciliationServiceTest: money must compare by value, not scale. */
    private static BigDecimal koboEq(String expected) {
        BigDecimal want = new BigDecimal(expected);
        return argThat(actual -> actual != null && actual.compareTo(want) == 0);
    }

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));

        FeeProperties feeProperties = new FeeProperties();
        feeProperties.setPayoutFlatFeeKobo(new BigDecimal("3000"));
        lenient().when(feeConfigService.current()).thenReturn(feeProperties);

        service = new PayoutService(payoutRepository, beneficiaryRepository, transactionRepository,
                merchantService, ledgerService, nombaTransferClient, transactionManager,
                feeConfigService, platformProfitService);

        merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .businessName("Test Merchant")
                .businessEmail("test@example.com")
                .passwordHash("hash")
                .build();

        beneficiary = Beneficiary.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .nickname("Main GTBank")
                .accountName("Jane Doe")
                .accountNumber("0123456789")
                .bankCode("058")
                .bankName("GTBank")
                .build();
    }

    // ---- applyWebhook (async payout_success / payout_failed / payout_refund) ----

    @Test
    void applyWebhookSuccess_marksSuccessAndMatchedWithoutCrediting() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED).reference("pyt_ref_1").build();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_ref_1").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.PROCESSING).build();

        when(payoutRepository.findByReferenceForUpdate("pyt_ref_1")).thenReturn(Optional.of(payout));

        service.applyWebhook(new NormalizedPayoutEvent("payout_success", "req-1", "pyt_ref_1",
                "nomba-tx-1", "sess-1", new BigDecimal("20"), new BigDecimal("100000")));

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(payout.getProviderReference()).isEqualTo("nomba-tx-1");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        // The webhook confirming the transfer IS the match for a payout — no separate requery to
        // compare against, unlike an inbound CUSTOMER_PAYMENT.
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);

        // Funds were already reserved at initiate() time — success must not credit anything.
        verifyNoInteractions(ledgerService);
        verifyNoInteractions(platformProfitService);
    }

    @Test
    void applyWebhookAlreadyTerminal_isNoOp() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.SUCCESSFUL)
                .matchStatus(MatchStatus.MATCHED).reference("pyt_ref_2").build();
        // Already finalized SUCCESS (e.g. by the synchronous initiate() response).
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_ref_2").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.SUCCESS).providerReference("nomba-tx-2").build();

        when(payoutRepository.findByReferenceForUpdate("pyt_ref_2")).thenReturn(Optional.of(payout));

        // A late/duplicate payout_failed webhook arrives after the payout already succeeded.
        service.applyWebhook(new NormalizedPayoutEvent("payout_failed", "req-2", "pyt_ref_2",
                null, null, null, new BigDecimal("100000")));

        // Must stay SUCCESS — a late failure signal cannot flip an already-confirmed success.
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        verifyNoInteractions(ledgerService);
        verifyNoInteractions(platformProfitService);
    }

    @Test
    void applyWebhookFailure_refundsWalletExactlyOnce() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED).reference("pyt_ref_3").build();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_ref_3").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.PROCESSING).build();

        when(payoutRepository.findByReferenceForUpdate("pyt_ref_3")).thenReturn(Optional.of(payout));

        service.applyWebhook(new NormalizedPayoutEvent("payout_failed", "req-3", "pyt_ref_3",
                null, null, null, new BigDecimal("100000")));

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        // Reserved amount + fee returned exactly once.
        verify(ledgerService, times(1)).credit(eq(merchant), koboEq("103000"), eq(tx),
                eq(LedgerEntryType.REVERSAL), anyString());
        verify(platformProfitService, times(1)).reverseOutflow(payout);
    }

    @Test
    void applyWebhookUnknownReference_isIgnored() {
        when(payoutRepository.findByReferenceForUpdate("no-such-ref")).thenReturn(Optional.empty());

        service.applyWebhook(new NormalizedPayoutEvent("payout_success", "req-4", "no-such-ref",
                "nomba-tx-4", "sess-4", new BigDecimal("20"), new BigDecimal("100000")));

        verifyNoInteractions(ledgerService);
        verifyNoInteractions(platformProfitService);
    }

    // ---- initiate() synchronous outcome, including races against the webhook ----

    private AtomicReference<Payout> stubReserve(BigDecimal amount) {
        AtomicReference<Payout> savedPayout = new AtomicReference<>();
        AtomicReference<Transaction> savedTx = new AtomicReference<>();

        when(merchantService.findById(merchant.getId())).thenReturn(merchant);
        when(beneficiaryRepository.findByIdAndMerchantId(beneficiary.getId(), merchant.getId()))
                .thenReturn(Optional.of(beneficiary));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            savedTx.set(t);
            return t;
        });
        // lenient: the race test where the webhook wins before the sync response never reaches this
        // lookup at all (finalizeAccepted's terminal-status guard returns first) — that's the exact
        // behavior being verified there, not an unused stub.
        lenient().when(transactionRepository.findById(any()))
                .thenAnswer(inv -> Optional.ofNullable(savedTx.get()));

        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> {
            Payout p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            savedPayout.set(p);
            return p;
        });
        when(payoutRepository.findByReferenceForUpdate(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(savedPayout.get()));

        return savedPayout;
    }

    @Test
    void initiateSyncSuccess_marksSuccessAndMatched() {
        AtomicReference<Payout> savedPayout = stubReserve(new BigDecimal("100000"));
        when(nombaTransferClient.transfer(any(), anyString()))
                .thenReturn(new NombaBankTransferData("nomba-tx-5", "SUCCESS", null, null, null));

        PayoutResponse response = service.initiate(merchant.getId(),
                new CreatePayoutRequest(beneficiary.getId(), new BigDecimal("100000"), "salary"));

        assertThat(response.status()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(savedPayout.get().getTransaction().getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(savedPayout.get().getTransaction().getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    void initiateRaceWebhookWinsBeforeSyncResponse_keepsWebhookOutcome() {
        AtomicReference<Payout> savedPayout = stubReserve(new BigDecimal("100000"));

        // Simulate the async webhook finalizing the payout to SUCCESS while the synchronous Nomba
        // call is still in flight — by the time our client gets a response and tries to finalize,
        // the row is already terminal.
        when(nombaTransferClient.transfer(any(), anyString())).thenAnswer(inv -> {
            savedPayout.get().setStatus(PayoutStatus.SUCCESS);
            savedPayout.get().setProviderReference("webhook-won");
            return new NombaBankTransferData("sync-response-id", "PENDING_BILLING", null, null, null);
        });

        PayoutResponse response = service.initiate(merchant.getId(),
                new CreatePayoutRequest(beneficiary.getId(), new BigDecimal("100000"), "salary"));

        // The synchronous PENDING_BILLING outcome must not overwrite the webhook's SUCCESS, and must
        // not touch the transaction at all (finalizeAccepted returns before reaching it).
        assertThat(response.status()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(response.providerReference()).isEqualTo("webhook-won");
        verify(transactionRepository, never()).findById(any());
    }

    @Test
    void initiateProviderRejection_refundsWalletOnce() {
        AtomicReference<Payout> savedPayout = stubReserve(new BigDecimal("100000"));
        when(nombaTransferClient.transfer(any(), anyString()))
                .thenThrow(new RuntimeException("provider rejected transfer"));

        PayoutResponse response = service.initiate(merchant.getId(),
                new CreatePayoutRequest(beneficiary.getId(), new BigDecimal("100000"), "salary"));

        assertThat(response.status()).isEqualTo(PayoutStatus.FAILED);
        assertThat(savedPayout.get().getTransaction().getStatus()).isEqualTo(TransactionStatus.FAILED);
        verify(ledgerService, times(1)).credit(eq(merchant), koboEq("103000"),
                eq(savedPayout.get().getTransaction()), eq(LedgerEntryType.REVERSAL), anyString());
        verify(platformProfitService, times(1)).reverseOutflow(savedPayout.get());
    }

    @Test
    void initiateProviderRejectionRaceWithWebhookSuccess_doesNotDoubleRefund() {
        AtomicReference<Payout> savedPayout = stubReserve(new BigDecimal("100000"));

        // The HTTP call actually succeeded at the provider and the webhook already confirmed it
        // (SUCCESS) — but our client only saw a timeout/exception locally.
        when(nombaTransferClient.transfer(any(), anyString())).thenAnswer(inv -> {
            savedPayout.get().setStatus(PayoutStatus.SUCCESS);
            throw new RuntimeException("read timeout");
        });

        PayoutResponse response = service.initiate(merchant.getId(),
                new CreatePayoutRequest(beneficiary.getId(), new BigDecimal("100000"), "salary"));

        // Must NOT refund — the money genuinely left. finalizeFailed's lock+recheck is what prevents
        // this exact scenario from double-refunding a payout that actually succeeded. (recordOutflow
        // at reserve() time is expected and unrelated — only the refund/reverseOutflow must not fire.)
        assertThat(response.status()).isEqualTo(PayoutStatus.SUCCESS);
        verify(ledgerService, never()).credit(any(), any(), any(), any(), anyString());
        verify(platformProfitService, never()).reverseOutflow(any());
    }

    // ---- sweepProcessingPayouts() (requery-based fallback for a missed webhook) ----

    @Test
    void sweepSuccess_marksSuccessAndMatched() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED).reference("pyt_sweep_1").build();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_sweep_1").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.PROCESSING).providerReference("nomba-tx-6").build();

        when(payoutRepository.findByStatusAndProviderReferenceIsNotNull(PayoutStatus.PROCESSING))
                .thenReturn(List.of(payout));
        when(nombaTransferClient.requeryTransfer("nomba-tx-6"))
                .thenReturn(new NombaBankTransferData("nomba-tx-6", "SUCCESS", null, null, null));
        when(payoutRepository.findByReferenceForUpdate("pyt_sweep_1")).thenReturn(Optional.of(payout));

        service.sweepProcessingPayouts();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        assertThat(tx.getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
        verifyNoInteractions(ledgerService);
    }

    @Test
    void sweepRaceAlreadyFinalizedByWebhook_doesNotReprocess() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.SUCCESSFUL)
                .matchStatus(MatchStatus.MATCHED).reference("pyt_sweep_2").build();
        // The list query saw PROCESSING, but by the time the sweep locks the row, the webhook has
        // already finalized it to SUCCESS.
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_sweep_2").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.SUCCESS).providerReference("nomba-tx-7").build();

        when(payoutRepository.findByStatusAndProviderReferenceIsNotNull(PayoutStatus.PROCESSING))
                .thenReturn(List.of(payout));
        // Even if the requery response looks like a terminal failure, the guard must win: a payout
        // that's already SUCCESS must never be refunded off a stale/racy requery result.
        when(nombaTransferClient.requeryTransfer("nomba-tx-7"))
                .thenReturn(new NombaBankTransferData("nomba-tx-7", "FAILED", null, null, null));
        when(payoutRepository.findByReferenceForUpdate("pyt_sweep_2")).thenReturn(Optional.of(payout));

        service.sweepProcessingPayouts();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESSFUL);
        verifyNoInteractions(ledgerService);
        verifyNoInteractions(platformProfitService);
    }

    @Test
    void sweepTerminalFailure_refundsWalletOnce() {
        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID()).merchant(merchant).type(TransactionType.PAYOUT)
                .amount(new BigDecimal("100000")).status(TransactionStatus.PENDING)
                .matchStatus(MatchStatus.UNMATCHED).reference("pyt_sweep_3").build();
        Payout payout = Payout.builder()
                .id(UUID.randomUUID()).merchant(merchant).beneficiary(beneficiary).transaction(tx)
                .reference("pyt_sweep_3").amount(new BigDecimal("100000")).fee(new BigDecimal("3000"))
                .status(PayoutStatus.PROCESSING).providerReference("nomba-tx-8").build();

        when(payoutRepository.findByStatusAndProviderReferenceIsNotNull(PayoutStatus.PROCESSING))
                .thenReturn(List.of(payout));
        when(nombaTransferClient.requeryTransfer("nomba-tx-8"))
                .thenReturn(new NombaBankTransferData("nomba-tx-8", "REFUND", null, null, null));
        when(payoutRepository.findByReferenceForUpdate("pyt_sweep_3")).thenReturn(Optional.of(payout));

        service.sweepProcessingPayouts();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        verify(ledgerService, times(1)).credit(eq(merchant), koboEq("103000"), eq(tx),
                eq(LedgerEntryType.REVERSAL), anyString());
        verify(platformProfitService, times(1)).reverseOutflow(payout);
    }
}
