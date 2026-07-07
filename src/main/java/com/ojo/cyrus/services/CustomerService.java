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
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.StatementRowResponse;
import com.ojo.cyrus.models.responses.StatementSummaryResponse;
import com.ojo.cyrus.nomba.NombaVirtualAccountClient;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import com.ojo.cyrus.repositories.MerchantCustomerRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

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

        Page<Transaction> transactions =
                transactionRepository.findStatementRows(customerId, from, to, matchStatus, pageable);

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
