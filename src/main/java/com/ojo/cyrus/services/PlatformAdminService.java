package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.NombaProperties;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantRole;
import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.exception.ForbiddenException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.entities.Wallet;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.Custody;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.LedgerIntegrity;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.OrphansAndStuck;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.ReconciliationHealth;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse.Totals;
import com.ojo.cyrus.models.responses.VirtualAccountAuditResponse;
import com.ojo.cyrus.models.responses.VirtualAccountAuditResponse.VirtualAccountAuditItem;
import com.ojo.cyrus.nomba.clients.NombaBalanceClient;
import com.ojo.cyrus.nomba.clients.NombaVirtualAccountClient;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountDetail;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountFilterRequest;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountListPage;
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
import java.time.Instant;
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
    private final NombaVirtualAccountClient nombaVirtualAccountClient;
    private final NombaProperties nombaProperties;
    private final PlatformTransactionManager transactionManager;

    private static final int VA_AUDIT_PAGE_LIMIT = 100;
    // Mirrors MissingWebhookSweepService's guard: a non-advancing/perpetually-non-empty cursor
    // (provider bug) must not spin the request thread forever.
    private static final int VA_AUDIT_MAX_PAGES = 50;
    private static final int VA_AUDIT_MAX_ITEMS_PER_BUCKET = 100;

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

    /**
     * Diffs Cyrus's local {@code VirtualAccount} table against Nomba's live list. Read-only and
     * on-demand (an admin action, not a scheduled sweep) — same two-phase shape as
     * {@link #getOverview()}: local rows are read (with the association fetch-joined so nothing is
     * read lazily after the tx closes) in one short read-only tx, then the Nomba list call runs with
     * no tx open.
     */
    public VirtualAccountAuditResponse auditVirtualAccounts() {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        List<VirtualAccount> localVAs = readTx.execute(status -> virtualAccountRepository.findAllWithCustomerAndMerchant());

        Map<String, NombaVirtualAccountDetail> nombaByRef = new HashMap<>();
        boolean nombaListTruncated = fetchAllNombaVirtualAccounts(nombaByRef);

        Map<String, VirtualAccount> localByRef = new HashMap<>();
        for (VirtualAccount va : localVAs) {
            localByRef.put(va.getMerchantCustomer().getExternalCustomerId(), va);
        }

        List<VirtualAccountAuditItem> leaked = new ArrayList<>();
        for (Map.Entry<String, NombaVirtualAccountDetail> entry : nombaByRef.entrySet()) {
            if (!localByRef.containsKey(entry.getKey())) {
                NombaVirtualAccountDetail d = entry.getValue();
                leaked.add(new VirtualAccountAuditItem(
                        d.accountRef(), d.bankAccountNumber(), null, null, nombaStatus(d)));
            }
        }
        boolean leakedTruncated = leaked.size() > VA_AUDIT_MAX_ITEMS_PER_BUCKET;
        if (leakedTruncated) leaked = new ArrayList<>(leaked.subList(0, VA_AUDIT_MAX_ITEMS_PER_BUCKET));

        List<VirtualAccountAuditItem> missing = new ArrayList<>();
        List<VirtualAccountAuditItem> drift = new ArrayList<>();
        for (VirtualAccount va : localVAs) {
            String ref = va.getMerchantCustomer().getExternalCustomerId();
            String merchantName = va.getMerchantCustomer().getMerchant().getBusinessName();
            NombaVirtualAccountDetail nomba = nombaByRef.get(ref);
            if (nomba == null) {
                missing.add(new VirtualAccountAuditItem(
                        ref, va.getAccountNumber(), merchantName, va.getStatus().name(), null));
                continue;
            }
            // CLOSED is the only local status that should ever correspond to Nomba's `expired`
            // (ACTIVE/SUSPENDED/PENDING are all "not expired" on Nomba's side) — see
            // CustomerService.updateStatus, which expires the Nomba VA before persisting CLOSED locally.
            boolean localClosed = va.getStatus() == VirtualAccountStatus.CLOSED;
            if (localClosed != nomba.expired()) {
                drift.add(new VirtualAccountAuditItem(
                        ref, va.getAccountNumber(), merchantName, va.getStatus().name(), nombaStatus(nomba)));
            }
        }
        boolean missingTruncated = missing.size() > VA_AUDIT_MAX_ITEMS_PER_BUCKET;
        if (missingTruncated) missing = new ArrayList<>(missing.subList(0, VA_AUDIT_MAX_ITEMS_PER_BUCKET));
        boolean driftTruncated = drift.size() > VA_AUDIT_MAX_ITEMS_PER_BUCKET;
        if (driftTruncated) drift = new ArrayList<>(drift.subList(0, VA_AUDIT_MAX_ITEMS_PER_BUCKET));

        if (leakedTruncated || missingTruncated || driftTruncated) {
            log.warn("Virtual account audit: result truncated at {} items/bucket (leakedOnNomba={}, missingOnNomba={}, statusDrift={})",
                    VA_AUDIT_MAX_ITEMS_PER_BUCKET, leakedTruncated, missingTruncated, driftTruncated);
        }

        return new VirtualAccountAuditResponse(
                localVAs.size(), nombaByRef.size(),
                leaked, leakedTruncated, missing, missingTruncated, drift, driftTruncated,
                nombaListTruncated, Instant.now());
    }

    private static String nombaStatus(NombaVirtualAccountDetail detail) {
        return detail.expired() ? "EXPIRED" : "ACTIVE";
    }

    /** @return true if Nomba's list was cut off by the page cap with more data remaining */
    private boolean fetchAllNombaVirtualAccounts(Map<String, NombaVirtualAccountDetail> byRef) {
        String cursor = null;
        int pages = 0;
        do {
            NombaVirtualAccountListPage page = nombaVirtualAccountClient.listVirtualAccounts(
                    NombaVirtualAccountFilterRequest.empty(), VA_AUDIT_PAGE_LIMIT, cursor);
            List<NombaVirtualAccountDetail> results = page.results() != null ? page.results() : List.of();
            for (NombaVirtualAccountDetail d : results) {
                byRef.put(d.accountRef(), d);
            }
            cursor = page.cursor();
            pages++;
        } while (cursor != null && !cursor.isBlank() && pages < VA_AUDIT_MAX_PAGES);

        boolean truncated = cursor != null && !cursor.isBlank();
        if (truncated) {
            log.warn("Virtual account audit: stopped after {} pages with a cursor still remaining — "
                    + "Nomba holds more than {} virtual accounts", pages, VA_AUDIT_MAX_PAGES * VA_AUDIT_PAGE_LIMIT);
        }
        return truncated;
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
