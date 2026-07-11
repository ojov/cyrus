package com.ojo.cyrus.repositories;

import com.ojo.cyrus.models.entities.PlatformProfitEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface PlatformProfitEntryRepository extends JpaRepository<PlatformProfitEntry, UUID> {

    // NOTE: deliberately no SUM-over-all-entries method — the raw sum of every signed entry is NOT
    // the expected provider balance (fee accruals are carved out of gross inflows, not additional
    // to them). The identity lives in PoolReconciliationService.expectedBalance.

    /** Sum of all INFLOW entries (confirmed payments received). */
    @Query("SELECT COALESCE(SUM(e.amountKobo), 0) FROM PlatformProfitEntry e WHERE e.entryType = com.ojo.cyrus.enums.PlatformProfitEntryType.PROFIT_INFLOW")
    BigDecimal sumInflows();

    /** Sum of all OUTFLOW entries (payouts sent). */
    @Query("SELECT COALESCE(SUM(e.amountKobo), 0) FROM PlatformProfitEntry e WHERE e.entryType = com.ojo.cyrus.enums.PlatformProfitEntryType.PROFIT_OUTFLOW")
    BigDecimal sumOutflows();

    /** Sum of all FEE_ACCRUAL entries (platform fees earned). */
    @Query("SELECT COALESCE(SUM(e.amountKobo), 0) FROM PlatformProfitEntry e WHERE e.entryType = com.ojo.cyrus.enums.PlatformProfitEntryType.PROFIT_FEE_ACCRUAL")
    BigDecimal sumFeeAccruals();
}
