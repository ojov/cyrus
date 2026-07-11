package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.PlatformProfitEntryType;
import com.ojo.cyrus.models.entities.PlatformProfitEntry;
import com.ojo.cyrus.models.entities.Payout;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.repositories.PlatformProfitEntryRepository;
import com.ojo.cyrus.utils.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The single writer of platform profit ledger entries. Every entry is append-only and written
 * in the same transaction as the corresponding merchant wallet posting, ensuring both ledgers
 * stay consistent. The running total is derived via {@code SUM(amount_kobo)}.
 *
 * <p>Callers must invoke these methods inside their own transaction — this service does not
 * manage transactions itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformProfitService {

    private final PlatformProfitEntryRepository repository;

    /** Records a confirmed inbound payment (gross amount flowing into Nomba). */
    public void recordInflow(Transaction tx) {
        record(tx, null, PlatformProfitEntryType.PROFIT_INFLOW, tx.getAmount(),
                "Payment " + tx.getReference());
    }

    /**
     * Records an outbound payout submission (debit from Nomba) plus the flat payout fee Cyrus
     * earns on it. The fee is charged to the merchant's wallet but never leaves the Nomba pool —
     * it is Cyrus profit, so it accrues here in the same breath as the outflow (and is reversed
     * with it if the payout fails).
     */
    public void recordOutflow(Payout payout) {
        record(null, payout, PlatformProfitEntryType.PROFIT_OUTFLOW,
                payout.getAmount().add(payout.getFee()).negate(),
                "Payout " + payout.getReference());
        if (payout.getFee().signum() != 0) {
            record(null, payout, PlatformProfitEntryType.PROFIT_FEE_ACCRUAL,
                    payout.getFee(),
                    "Payout fee for " + payout.getReference());
        }
    }

    /** Reverses a previous outflow and its paired fee accrual (e.g. failed payout refund). */
    public void reverseOutflow(Payout payout) {
        record(null, payout, PlatformProfitEntryType.PROFIT_OUTFLOW,
                payout.getAmount().add(payout.getFee()),
                "Payout refund " + payout.getReference());
        if (payout.getFee().signum() != 0) {
            record(null, payout, PlatformProfitEntryType.PROFIT_FEE_ACCRUAL,
                    payout.getFee().negate(),
                    "Reversal of payout fee for " + payout.getReference());
        }
    }

    /** Records a reversal of a previously confirmed inflow. */
    public void reverseInflow(Transaction tx, BigDecimal netAmount) {
        record(tx, null, PlatformProfitEntryType.PROFIT_INFLOW, netAmount.negate(),
                "Reversal of " + tx.getReference());
    }

    /**
     * Records a platform fee accrual (positive = profit earned, negative = profit clawed back on a
     * reversal). Zero/null amounts are skipped — negative amounts are NOT: a reversal's claw-back
     * must land on the ledger or profit is overstated forever after.
     */
    public void recordFeeAccrual(Transaction tx, BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        record(tx, null, PlatformProfitEntryType.PROFIT_FEE_ACCRUAL, amount,
                (amount.signum() > 0 ? "Platform fee for " : "Fee claw-back on reversal of ") + tx.getReference());
    }

    private void record(Transaction tx, Payout payout, PlatformProfitEntryType type,
                        BigDecimal amountKobo, String description) {
        PlatformProfitEntry entry = PlatformProfitEntry.builder()
                .transaction(tx)
                .payout(payout)
                .entryType(type)
                .amountKobo(MoneyUtil.normalize(amountKobo))
                .description(description)
                .build();
        repository.save(entry);
        log.info("Profit ledger {} {} kobo ({})", type, amountKobo, description);
    }
}
