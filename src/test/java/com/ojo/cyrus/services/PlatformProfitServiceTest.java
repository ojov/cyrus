package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.PlatformProfitEntryType;
import com.ojo.cyrus.models.entities.Payout;
import com.ojo.cyrus.models.entities.PlatformProfitEntry;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.repositories.PlatformProfitEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlatformProfitServiceTest {

    @Mock
    private PlatformProfitEntryRepository repository;

    private PlatformProfitService service;

    @BeforeEach
    void setUp() {
        service = new PlatformProfitService(repository);
    }

    private List<PlatformProfitEntry> savedEntries() {
        ArgumentCaptor<PlatformProfitEntry> captor = ArgumentCaptor.forClass(PlatformProfitEntry.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void feeAccrualRecordsNegativeClawback() {
        // The reversal path passes a NEGATIVE amount — it must be recorded, not silently skipped,
        // or profit is overstated forever after a reversal.
        Transaction tx = Transaction.builder().reference("txn_1").build();
        service.recordFeeAccrual(tx, new BigDecimal("-3750"));

        List<PlatformProfitEntry> entries = savedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getEntryType()).isEqualTo(PlatformProfitEntryType.PROFIT_FEE_ACCRUAL);
        assertThat(entries.getFirst().getAmountKobo()).isEqualByComparingTo("-3750");
    }

    @Test
    void feeAccrualSkipsZeroAndNull() {
        Transaction tx = Transaction.builder().reference("txn_2").build();
        service.recordFeeAccrual(tx, BigDecimal.ZERO);
        service.recordFeeAccrual(tx, null);
        verifyNoInteractions(repository);
    }

    @Test
    void outflowRecordsPairedFeeAccrual() {
        // The payout fee is charged to the merchant but never leaves the pool — it is profit and
        // must accrue alongside the outflow.
        Payout payout = Payout.builder()
                .reference("pyt_1")
                .amount(new BigDecimal("100000"))
                .fee(new BigDecimal("3000"))
                .build();
        service.recordOutflow(payout);

        List<PlatformProfitEntry> entries = savedEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getEntryType()).isEqualTo(PlatformProfitEntryType.PROFIT_OUTFLOW);
        assertThat(entries.get(0).getAmountKobo()).isEqualByComparingTo("-103000");
        assertThat(entries.get(1).getEntryType()).isEqualTo(PlatformProfitEntryType.PROFIT_FEE_ACCRUAL);
        assertThat(entries.get(1).getAmountKobo()).isEqualByComparingTo("3000");
    }

    @Test
    void reverseOutflowNetsBothLegsToZero() {
        Payout payout = Payout.builder()
                .reference("pyt_2")
                .amount(new BigDecimal("100000"))
                .fee(new BigDecimal("3000"))
                .build();
        service.recordOutflow(payout);
        service.reverseOutflow(payout);

        List<PlatformProfitEntry> entries = savedEntries();
        assertThat(entries).hasSize(4);
        BigDecimal outflowNet = entries.stream()
                .filter(e -> e.getEntryType() == PlatformProfitEntryType.PROFIT_OUTFLOW)
                .map(PlatformProfitEntry::getAmountKobo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal accrualNet = entries.stream()
                .filter(e -> e.getEntryType() == PlatformProfitEntryType.PROFIT_FEE_ACCRUAL)
                .map(PlatformProfitEntry::getAmountKobo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(outflowNet).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(accrualNet).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroFeePayoutSkipsAccrualLeg() {
        // Free payouts (admin-set zero fee) record only the outflow, no zero-amount accrual noise.
        Payout payout = Payout.builder()
                .reference("pyt_3")
                .amount(new BigDecimal("100000"))
                .fee(new BigDecimal("0"))
                .build();
        service.recordOutflow(payout);

        List<PlatformProfitEntry> entries = savedEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getEntryType()).isEqualTo(PlatformProfitEntryType.PROFIT_OUTFLOW);
    }

    @Test
    void amountsNormalizedToScale4() {
        Transaction tx = Transaction.builder().reference("txn_3").build();
        service.recordFeeAccrual(tx, new BigDecimal("1500.015"));

        PlatformProfitEntry entry = savedEntries().getFirst();
        assertThat(entry.getAmountKobo().scale()).isEqualTo(4);
        assertThat(entry.getAmountKobo()).isEqualByComparingTo("1500.015");
    }

    @Test
    void inflowAndReversalNetToZero() {
        Transaction tx = Transaction.builder()
                .reference("txn_4")
                .amount(new BigDecimal("250000"))
                .build();
        service.recordInflow(tx);
        service.reverseInflow(tx, tx.getAmount());

        List<PlatformProfitEntry> entries = savedEntries();
        assertThat(entries).hasSize(2);
        BigDecimal inflowNet = entries.stream()
                .filter(e -> e.getEntryType() == PlatformProfitEntryType.PROFIT_INFLOW)
                .map(PlatformProfitEntry::getAmountKobo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(inflowNet).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
