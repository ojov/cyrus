package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantRole;
import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.exception.ForbiddenException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Wallet;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.Custody;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.LedgerIntegrity;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.OrphansAndStuck;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.ReconciliationHealth;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.Totals;
import com.ojo.cyrus.nomba.clients.NombaBalanceClient;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.LedgerEntryRepository;
import com.ojo.cyrus.repositories.MerchantCustomerRepository;
import com.ojo.cyrus.repositories.MerchantRepository;
import com.ojo.cyrus.repositories.NombaPaymentEventRepository;
import com.ojo.cyrus.repositories.PayoutRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ojo.cyrus.utils.MoneyUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform-wide oversight for super-admins. Every endpoint on {@code /v1/platform/**} authorizes via
 * {@link #requireSuperAdmin(String)} — the JWT chain has already authenticated the caller; this adds
 * the role gate (reusing the same email→merchant lookup the rest of the dashboard uses).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final MerchantService merchantService;
    private final WalletRepository walletRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantCustomerRepository merchantCustomerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final TransactionRepository transactionRepository;
    private final NombaPaymentEventRepository nombaPaymentEventRepository;
    private final PayoutRepository payoutRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NombaBalanceClient nombaBalanceClient;
    private final NombaProperties nombaProperties;
    private final PlatformTransactionManager transactionManager;

    /** Authorizes the caller as a super-admin, or throws {@link ForbiddenException} (→ 403). */
    public void requireSuperAdmin(String email) {
        Merchant merchant = merchantService.findByBusinessEmail(email);
        if (merchant.getRole() != MerchantRole.SUPER_ADMIN) {
            throw new ForbiddenException("Super-admin access required");
        }
    }

    /** Everything the DB can tell us, materialized to plain values so no tx is held across the Nomba call. */
    private record DbAggregates(BigDecimal walletLiabilitiesKobo, Totals totals, ReconciliationHealth reconciliation,
                                OrphansAndStuck orphansAndStuck, LedgerIntegrity ledgerIntegrity) {}

    /**
     * Assembles the platform snapshot: all DB aggregation happens in one short read-only tx (so lazy
     * merchant names on stuck payouts / mismatched wallets load safely and no connection is held for
     * the provider's latency), then the live Nomba balance is fetched with NO tx open.
     */
    public PlatformOverviewResponse getOverview() {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        DbAggregates db = readTx.execute(status -> buildDbAggregates());

        BigDecimal nombaBalanceKobo = fetchNombaBalanceOrNull();
        boolean available = nombaBalanceKobo != null;
        BigDecimal coverage = available ? nombaBalanceKobo.subtract(db.walletLiabilitiesKobo()) : null;
        Custody custody = new Custody(db.walletLiabilitiesKobo(), nombaBalanceKobo, coverage, available);

        return new PlatformOverviewResponse(
                custody, db.totals(), db.reconciliation(), db.orphansAndStuck(), db.ledgerIntegrity());
    }

    private DbAggregates buildDbAggregates() {
        BigDecimal liabilities = walletRepository.sumAllBalances();

        Totals totals = new Totals(
                merchantRepository.count(),
                merchantCustomerRepository.count(),
                virtualAccountRepository.count(),
                transactionRepository.count(),
                transactionRepository.sumAmountByTypeAndStatus(TransactionType.CUSTOMER_PAYMENT, TransactionStatus.SUCCESSFUL),
                transactionRepository.sumAmountByTypeAndStatus(TransactionType.PAYOUT, TransactionStatus.SUCCESSFUL));

        ReconciliationHealth reconciliation = new ReconciliationHealth(
                transactionRepository.countByMatchStatus(MatchStatus.MATCHED),
                transactionRepository.countByMatchStatus(MatchStatus.DISCREPANCY),
                transactionRepository.countByMatchStatus(MatchStatus.MANUAL_REVIEW),
                transactionRepository.countByStatus(TransactionStatus.PENDING));

        List<OrphansAndStuck.StuckPayout> stuck = payoutRepository
                .findByStatusOrderByCreatedAtDesc(PayoutStatus.PROCESSING).stream()
                .map(p -> new OrphansAndStuck.StuckPayout(
                        p.getId(), p.getReference(),
                        p.getMerchant() != null ? p.getMerchant().getBusinessName() : null,
                        p.getAmount(), p.getCreatedAt().toString()))
                .toList();
        OrphansAndStuck orphansAndStuck = new OrphansAndStuck(
                nombaPaymentEventRepository.countByMerchantIsNull(), stuck.size(), stuck);

        return new DbAggregates(liabilities, totals, reconciliation, orphansAndStuck, buildLedgerIntegrity());
    }

    private LedgerIntegrity buildLedgerIntegrity() {
        Map<UUID, BigDecimal> ledgerByWallet = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.sumAmountGroupedByWallet()) {
            ledgerByWallet.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<Wallet> wallets = walletRepository.findAll();
        List<LedgerIntegrity.WalletMismatch> mismatches = new ArrayList<>();
        for (Wallet w : wallets) {
            BigDecimal ledgerSum = ledgerByWallet.getOrDefault(w.getId(), MoneyUtil.ZERO_KOBO);
            if (ledgerSum.compareTo(w.getAvailableBalance()) != 0) {
                mismatches.add(new LedgerIntegrity.WalletMismatch(
                        w.getId(),
                        w.getMerchant() != null ? w.getMerchant().getBusinessName() : null,
                        w.getAvailableBalance(), ledgerSum));
            }
        }
        return new LedgerIntegrity(wallets.size(), mismatches.size(), mismatches.isEmpty(), mismatches);
    }

    /** Live provider balance (kobo), or {@code null} if the Nomba call fails — the snapshot degrades. */
    private BigDecimal fetchNombaBalanceOrNull() {
        try {
            String sub = nombaProperties.subAccountId();
            NombaBalanceData data = (sub != null && !sub.isBlank())
                    ? nombaBalanceClient.getSubAccountBalance(sub)
                    : nombaBalanceClient.getParentAccountBalance();
            return NombaCurrencyUtil.nairaToKobo(data.amount());
        } catch (RuntimeException e) {
            log.warn("Live Nomba balance unavailable for platform overview: {}", e.getMessage());
            return null;
        }
    }
}
