package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantCustomerStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.exception.InvalidCustomerStateException;
import com.ojo.cyrus.models.dto.CustomerRenameSnapshot;
import com.ojo.cyrus.models.dto.CustomerStatusSnapshot;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.MerchantCustomer;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerRequest;
import com.ojo.cyrus.models.responses.CustomerDetailResponse;
import com.ojo.cyrus.models.responses.CustomerListItemResponse;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.StatementRowResponse;
import com.ojo.cyrus.models.responses.StatementSummaryResponse;
import com.ojo.cyrus.nomba.clients.NombaVirtualAccountClient;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountDetail;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountLookup;
import com.ojo.cyrus.repositories.MerchantCustomerRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.Mapper;
import com.ojo.cyrus.utils.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer identity + virtual-account lifecycle. Provisioning and the Nomba-side rename/expire calls
 * go through {@link NombaVirtualAccountClient} against Cyrus's own single Nomba account — no merchant
 * credentials are threaded any more. The two-phase (no-DB-transaction-across-HTTP) pattern is kept
 * for the rename/status flows so a failed Nomba call never leaves a split-brain state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final MerchantCustomerRepository merchantCustomerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final NombaVirtualAccountClient nombaVirtualAccountClient;
    private final PlatformTransactionManager transactionManager;

    public CustomerResponse create(UUID merchantId, CreateCustomerRequest request) {
        if (merchantCustomerRepository.existsByMerchantIdAndExternalCustomerId(merchantId, request.reference())) {
            throw new AlreadyExistsException("A customer with this reference already exists");
        }

        Merchant merchant = merchantService.findById(merchantId);

        // Build in memory (not yet persisted) so we can provision at Nomba first and write nothing
        // if provisioning fails. The Nomba call runs with no DB transaction open.
        MerchantCustomer customer = Mapper.toMerchantCustomer(merchant, request);
        NombaVirtualAccountData nombaData = nombaVirtualAccountClient.createVirtualAccount(
                Mapper.toNombaRequest(customer, request.bvn()));

        return new TransactionTemplate(transactionManager).execute(_ -> {
            MerchantCustomer saved = merchantCustomerRepository.save(customer);
            VirtualAccount va = virtualAccountRepository.save(Mapper.toVirtualAccount(saved, nombaData));
            log.info("Created customer {} for merchant {} with virtual account {}",
                    saved.getExternalCustomerId(), merchantId, va.getAccountNumber());
            return Mapper.toCustomerResponse(saved, va);
        });
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByReference(UUID merchantId, String reference) {
        MerchantCustomer customer = requireCustomer(merchantId, reference);
        VirtualAccount va = requireVirtualAccount(customer.getId());
        return Mapper.toCustomerResponse(customer, va);
    }

    /**
     * Dashboard-only: {@link #getByReference} plus a live authenticity check of the virtual account
     * against Nomba directly (cached — see {@link NombaVirtualAccountClient#getVirtualAccountCached}).
     * Local data is read in a short read-only tx that closes before the Nomba call, per the
     * never-hold-a-tx-across-an-external-call rule. The Nomba call is genuinely best-effort: any
     * failure (network, Nomba down, the VA missing on Nomba entirely) degrades to
     * {@code checked=false} rather than failing the whole customer lookup — an ops dashboard should
     * still show what Cyrus knows locally even when the live check can't complete. A failure here is
     * a signal for the bulk super-admin VA audit, not something this per-customer check needs to
     * diagnose itself.
     */
    public CustomerDetailResponse getByReferenceWithNombaVerification(UUID merchantId, String reference) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        CustomerResponse customer = readTx.execute(_ -> {
            MerchantCustomer c = requireCustomer(merchantId, reference);
            VirtualAccount va = requireVirtualAccount(c.getId());
            return Mapper.toCustomerResponse(c, va);
        });

        return new CustomerDetailResponse(customer, verifyAgainstNomba(customer));
    }

    private CustomerDetailResponse.NombaVerification verifyAgainstNomba(CustomerResponse customer) {
        String accountRef = customer.reference();
        NombaVirtualAccountLookup lookup;
        try {
            lookup = nombaVirtualAccountClient.getVirtualAccountCached(accountRef);
        } catch (RuntimeException e) {
            log.warn("Nomba VA verification unavailable for customer {}: {}", accountRef, e.getMessage());
            return new CustomerDetailResponse.NombaVerification(
                    false, false, List.of("Live verification against Nomba is temporarily unavailable"),
                    false, Instant.now());
        }

        NombaVirtualAccountDetail nomba = lookup.detail();
        CustomerResponse.VirtualAccountSummary local = customer.virtualAccount();
        List<String> discrepancies = new ArrayList<>();

        if (!Objects.equals(local.accountNumber(), nomba.bankAccountNumber())) {
            discrepancies.add("Account number differs from Nomba");
        }
        if (local.accountName() == null || !local.accountName().equalsIgnoreCase(nomba.bankAccountName())) {
            discrepancies.add("Account name differs from Nomba");
        }
        if (local.bankName() == null || !local.bankName().equalsIgnoreCase(nomba.bankName())) {
            discrepancies.add("Bank name differs from Nomba");
        }
        boolean localClosed = "CLOSED".equals(local.status());
        if (localClosed != nomba.expired()) {
            discrepancies.add("Status drift: local is " + local.status() + " but Nomba " +
                    (nomba.expired() ? "has expired this account" : "still shows it active"));
        }

        return new CustomerDetailResponse.NombaVerification(
                true, discrepancies.isEmpty(), discrepancies, lookup.fromCache(), lookup.fetchedAt());
    }

    /**
     * Paginated, newest-first list of every customer for this merchant, each with their virtual
     * account and lifetime received volume. Lifetime totals are fetched in one grouped query for
     * the whole page rather than per-row, to avoid an N+1 against the transaction table.
     */
    @Transactional(readOnly = true)
    public Page<CustomerListItemResponse> list(UUID merchantId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MerchantCustomer> customers = merchantCustomerRepository.findByMerchantId(merchantId, sorted);

        List<UUID> customerIds = customers.getContent().stream().map(MerchantCustomer::getId).toList();
        Map<UUID, BigDecimal> lifetimeByCustomerId = customerIds.isEmpty() ? Map.of()
                : transactionRepository.sumAmountByCustomerIdsAndStatus(merchantId, customerIds, TransactionStatus.SUCCESSFUL)
                        .stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));

        return customers.map(customer -> Mapper.toCustomerListItemResponse(customer,
                lifetimeByCustomerId.getOrDefault(customer.getId(), MoneyUtil.ZERO_KOBO)));
    }

    /**
     * A customer's identity, a reporting summary (always over their full history), and a
     * paginated, newest-first transaction history — optionally narrowed to a date range and/or
     * a single {@link MatchStatus} (e.g. pull up just the {@code DISCREPANCY}/{@code MANUAL_REVIEW}
     * exception rows). Scoped to {@code merchantId} so one merchant can never read another
     * merchant's customer data.
     */
    @Transactional(readOnly = true)
    public CustomerStatementResponse getStatement(UUID merchantId, String reference, Instant from, Instant to,
            MatchStatus matchStatus, Pageable pageable) {
        MerchantCustomer customer = requireCustomer(merchantId, reference);
        VirtualAccount va = requireVirtualAccount(customer.getId());
        UUID customerId = customer.getId();

        // Built via Specification rather than a `(:param IS NULL OR ...)` JPQL guard — Postgres's
        // JDBC driver can't infer a bind parameter's type when the value is actually null and the
        // only context is an `IS NULL` check (a real "could not determine data type of parameter"
        // failure, caught live). Specification simply omits the predicate for an absent filter, so
        // no null ever gets bound for a skipped one.
        Specification<Transaction> spec = (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("receivedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("receivedAt"), to));
        }
        if (matchStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("matchStatus"), matchStatus));
        }
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<Transaction> transactions = transactionRepository.findAll(spec, sorted);

        StatementSummaryResponse summary = new StatementSummaryResponse(
                transactionRepository.sumAmountByCustomerAndStatus(customerId, TransactionStatus.SUCCESSFUL),
                transactionRepository.countByCustomerId(customerId),
                transactionRepository.countByCustomerIdAndStatus(customerId, TransactionStatus.PENDING),
                transactionRepository.sumAmountByCustomerAndStatus(customerId, TransactionStatus.PENDING),
                transactionRepository.countByCustomerIdAndMatchStatus(customerId, MatchStatus.MANUAL_REVIEW),
                transactionRepository.countByCustomerIdAndMatchStatus(customerId, MatchStatus.DISCREPANCY),
                transactionRepository.findLastReceivedAtByCustomerId(customerId));

        return new CustomerStatementResponse(
                Mapper.toCustomerResponse(customer, va),
                summary,
                transactions.map(tx -> new StatementRowResponse(
                        tx.getReceivedAt(),
                        tx.getPayerName(),
                        tx.getProviderTransactionId(),
                        tx.getMatchStatus().name(),
                        tx.getAmount())));
    }

    /**
     * Partial update of the customer's own profile fields. When firstName/lastName changes, the
     * virtual account's Nomba-side bank account name is renamed to match; email/phone-only changes
     * never call Nomba. NOT_SUPPORTED keeps the Nomba call outside any DB transaction — it must
     * succeed before any local field is persisted.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerResponse rename(UUID merchantId, String reference, UpdateCustomerRequest request) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        CustomerRenameSnapshot current = readTx.execute(_ -> {
            MerchantCustomer customer = requireCustomer(merchantId, reference);
            return new CustomerRenameSnapshot(customer.getExternalCustomerId(), customer.getFirstName(),
                    customer.getLastName());
        });

        String newAccountName = null;
        if (request.firstName() != null || request.lastName() != null) {
            String firstName = request.firstName() != null ? request.firstName() : current.firstName();
            String lastName = request.lastName() != null ? request.lastName() : current.lastName();
            newAccountName = (lastName != null && !lastName.isBlank()) ? firstName + " " + lastName : firstName;
            nombaVirtualAccountClient.updateVirtualAccountName(current.reference(), newAccountName);
        }

        String finalAccountName = newAccountName;
        return new TransactionTemplate(transactionManager).execute(_ -> {
            MerchantCustomer customer = requireCustomer(merchantId, reference);
            VirtualAccount va = requireVirtualAccount(customer.getId());

            if (request.firstName() != null) {
                customer.setFirstName(request.firstName());
            }
            if (request.lastName() != null) {
                customer.setLastName(request.lastName());
            }
            if (request.email() != null) {
                customer.setEmail(request.email());
            }
            if (request.phoneNumber() != null) {
                customer.setPhoneNumber(request.phoneNumber());
            }
            if (finalAccountName != null) {
                va.setAccountName(finalAccountName);
            }

            log.info("Updated profile for customer {} (merchant {})", reference, merchantId);
            return Mapper.toCustomerResponse(customer, va);
        });
    }

    /**
     * Sets the customer's KYC tier. Cyrus doesn't verify KYC — that's the merchant's own process — so
     * this is an unguarded set. The virtual account is untouched by a tier change.
     */
    @Transactional
    public CustomerResponse updateKycTier(UUID merchantId, String reference, KycTier tier) {
        MerchantCustomer customer = requireCustomer(merchantId, reference);
        customer.setKycTier(tier);
        VirtualAccount va = requireVirtualAccount(customer.getId());
        log.info("Set KYC tier {} for customer {} (merchant {})", tier, reference, merchantId);
        return Mapper.toCustomerResponse(customer, va);
    }

    /**
     * Sets the customer's status, cascading to their (1:1) virtual account. ACTIVE/SUSPENDED are
     * reversible and Cyrus-local only. CLOSED is terminal (soft-delete) and additionally expires
     * the VA on Nomba's side via {@link NombaVirtualAccountClient#expireVirtualAccount}. NOT_SUPPORTED
     * keeps the Nomba expiry call outside any DB transaction — it must succeed before the local
     * CLOSED status is persisted.
     *
     * <p>Ideally, SUSPENDED would also call Nomba's suspend endpoint
     * ({@code PUT /v1/accounts/suspend/{accountId}}) to halt inflows at the provider level, and
     * SUSPENDED→ACTIVE would call it again to reactivate. That endpoint exists in
     * {@link NombaVirtualAccountClient#suspendVirtualAccount} and in
     * {@link com.ojo.cyrus.nomba.NombaApiUri#SUSPEND_VIRTUAL_ACCOUNT} but was disabled on our
     * hackathon-provisioned Nomba account (returned 403), so the call is not wired into this flow.
     * Once Nomba enables the feature, add the Nomba call here (same two-phase pattern as
     * CLOSED/expire).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerResponse updateStatus(UUID merchantId, String reference, MerchantCustomerStatus status) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        CustomerStatusSnapshot current = readTx.execute(_ -> {
            MerchantCustomer customer = requireCustomer(merchantId, reference);
            if (customer.getStatus() == MerchantCustomerStatus.CLOSED) {
                throw new InvalidCustomerStateException("Customer is CLOSED — no further status changes are accepted");
            }
            return new CustomerStatusSnapshot(customer.getExternalCustomerId());
        });

        if (status == MerchantCustomerStatus.CLOSED) {
            nombaVirtualAccountClient.expireVirtualAccount(current.reference());
        }

        return new TransactionTemplate(transactionManager).execute(_ -> {
            MerchantCustomer customer = requireCustomer(merchantId, reference);
            // Re-check: a concurrent request could have closed this customer (and irreversibly expired
            // the VA on Nomba) between the read phase and here — don't overwrite CLOSED back to
            // ACTIVE/SUSPENDED locally while Nomba's VA stays expired.
            if (customer.getStatus() == MerchantCustomerStatus.CLOSED) {
                throw new InvalidCustomerStateException("Customer is CLOSED — no further status changes are accepted");
            }
            VirtualAccount va = requireVirtualAccount(customer.getId());

            customer.setStatus(status);
            va.setStatus(switch (status) {
                case ACTIVE -> VirtualAccountStatus.ACTIVE;
                case SUSPENDED -> VirtualAccountStatus.SUSPENDED;
                case CLOSED -> VirtualAccountStatus.CLOSED;
            });

            log.info("Set status {} for customer {} (merchant {})", status, reference, merchantId);
            return Mapper.toCustomerResponse(customer, va);
        });
    }

    private MerchantCustomer requireCustomer(UUID merchantId, String reference) {
        return merchantCustomerRepository.findByMerchantIdAndExternalCustomerId(merchantId, reference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    private VirtualAccount requireVirtualAccount(UUID customerId) {
        return virtualAccountRepository.findByMerchantCustomerId(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));
    }
}
