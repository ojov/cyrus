package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.models.responses.PlatformProfitSummaryResponse;
import com.ojo.cyrus.nomba.clients.NombaBalanceClient;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.PlatformProfitEntryRepository;
import com.ojo.cyrus.repositories.WalletRepository;
import com.ojo.cyrus.utils.MoneyUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Scheduled reconciliation sweep that compares the expected provider balance (sum of platform
 * profit entries) against the live Nomba balance. Runs periodically; persists the comparison
 * result and flags discrepancies for review.
 *
 * <p>Does NOT auto-correct — discrepancies are surfaced to the super-admin for manual review.
 * Auto-correction is only safe for well-understood, rule-based deltas (e.g. rounding), which
 * should be added as specific rules here rather than a blanket "adjust to match" policy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Getter
public class PoolReconciliationService {

    private final PlatformProfitEntryRepository profitEntryRepository;
    private final WalletRepository walletRepository;
    private final NombaBalanceClient nombaBalanceClient;
    private final NombaProperties nombaProperties;
    private final PlatformTransactionManager transactionManager;

    /** Last sync result — in-memory only; persisted via the profit entry trail. */
    private volatile Instant lastSyncAt;

    private volatile String lastReconciliationStatus = "PENDING";

    /**
     * The expected provider balance is NOT the raw sum of all profit entries — that would
     * double-count fee accruals (the margin is carved out of the gross inflow, not additional to
     * it), ignore Nomba's own inflow fee, and overstate outflows by Cyrus's payout fee (which
     * never leaves the pool). The identity that actually holds across every lifecycle (inflow,
     * payout success/failure, payment reversal) is:
     *
     * <pre>expected pool balance = merchant liabilities (Σ wallet balances) + accrued profit (Σ fee accruals)</pre>
     *
     * — everything in the pool is either owed to a merchant or earned by Cyrus.
     */
    private BigDecimal expectedBalance(BigDecimal merchantLiabilities, BigDecimal feeAccruals) {
        return MoneyUtil.normalize(merchantLiabilities.add(feeAccruals));
    }

    /**
     * Builds the profit summary from the ledger, then fetches the live provider balance
     * and compares.
     */
    public PlatformProfitSummaryResponse getSummary() {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);

        BigDecimal[] results = readTx.execute(status -> new BigDecimal[]{
                profitEntryRepository.sumInflows(),
                profitEntryRepository.sumOutflows(),
                profitEntryRepository.sumFeeAccruals(),
                walletRepository.sumAllBalances()
        });
        BigDecimal totalInflows = results[0];
        BigDecimal totalOutflows = results[1];
        BigDecimal totalFeeAccruals = results[2];
        BigDecimal merchantLiabilities = results[3];
        BigDecimal expected = expectedBalance(merchantLiabilities, totalFeeAccruals);

        BigDecimal actualBalance = fetchNombaBalanceOrNull();
        BigDecimal delta = actualBalance != null ? expected.subtract(actualBalance) : null;

        return new PlatformProfitSummaryResponse(
                expected,
                actualBalance,
                delta,
                MoneyUtil.normalize(totalInflows),
                // Outflow entries are signed negative (with refunds netting positive) — present the
                // net magnitude sent out, not a negative number.
                MoneyUtil.normalize(totalOutflows.negate()),
                MoneyUtil.normalize(totalFeeAccruals),
                MoneyUtil.normalize(merchantLiabilities),
                lastSyncAt,
                lastReconciliationStatus
        );
    }

    /**
     * Periodic sweep: fetches live provider balance, compares against expected, and records
     * the result.
     *
     * <p>Transient deltas are expected in normal operation: a payment Nomba has settled but Cyrus
     * hasn't reconciled yet (actual briefly exceeds expected), or a payout reserved but not yet
     * executed by Nomba (expected briefly below actual). The tolerance absorbs rounding only —
     * a DISCREPANCY that persists across consecutive sweeps with no in-flight items is the real
     * signal for review.
     */
    @Scheduled(fixedDelayString = "#{${app.profit-reconciliation.delay-seconds:300} * 1000}")
    public void sweep() {
        BigDecimal expected;
        try {
            expected = expectedBalance(walletRepository.sumAllBalances(),
                    profitEntryRepository.sumFeeAccruals());
        } catch (Exception e) {
            log.error("Failed to read profit ledger for reconciliation sweep", e);
            lastReconciliationStatus = "LEDGER_READ_FAILED";
            return;
        }

        BigDecimal actualBalance = fetchNombaBalanceOrNull();
        if (actualBalance == null) {
            log.warn("Pool reconciliation sweep: provider balance unavailable — skipping");
            lastReconciliationStatus = "PROVIDER_UNAVAILABLE";
            return;
        }

        BigDecimal delta = expected.subtract(actualBalance);
        BigDecimal tolerance = new BigDecimal("100"); // ₦1.00 (100 kobo) tolerance

        if (delta.abs().compareTo(tolerance) <= 0) {
            lastReconciliationStatus = "MATCHED";
            log.info("Pool reconciliation: MATCHED (expected={}, actual={}, delta={})",
                    expected, actualBalance, delta);
        } else {
            lastReconciliationStatus = "DISCREPANCY";
            log.warn("Pool reconciliation: DISCREPANCY (expected={}, actual={}, delta={}) — review required",
                    expected, actualBalance, delta);
        }
        lastSyncAt = Instant.now();
    }

    private BigDecimal fetchNombaBalanceOrNull() {
        try {
            String sub = nombaProperties.subAccountId();
            NombaBalanceData data = (sub != null && !sub.isBlank())
                    ? nombaBalanceClient.getSubAccountBalance(sub)
                    : nombaBalanceClient.getParentAccountBalance();
            return NombaCurrencyUtil.nairaToKobo(data.amount());
        } catch (RuntimeException e) {
            log.warn("Pool reconciliation: Nomba balance fetch failed: {}", e.getMessage());
            return null;
        }
    }
}
