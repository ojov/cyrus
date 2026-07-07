package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.CustomerStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.exception.InvalidCustomerStateException;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.StatementRowResponse;
import com.ojo.cyrus.nomba.NombaClient;
import com.ojo.cyrus.nomba.dto.NombaCredentials;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import com.ojo.cyrus.repositories.CustomerRepository;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantService merchantService;
    private final NombaClient nombaClient;
    private final PlatformTransactionManager transactionManager;

    public CustomerResponse create(UUID merchantId, Environment env, CreateCustomerRequest request) {
        if (customerRepository.existsByMerchantIdAndReference(merchantId, request.reference())) {
            throw new AlreadyExistsException("A customer with this reference already exists");
        }

        Merchant merchant = merchantService.findById(merchantId);

        // Build the customer in memory (not yet persisted) so we can provision at the
        // provider first and write nothing if provisioning fails.
        Customer customer = Mapper.toCustomer(merchant, request);

        // Provision the virtual account at Nomba BEFORE opening a DB transaction — never
        // hold a database connection or row locks open across an external HTTP call.
        // Credentials are resolved inside a read-only tx so the merchant's lazy
        // @ElementCollections are materialized while the persistence context is open.
        NombaCredentials creds = merchantService.getNombaCredentials(merchantId);
        NombaVirtualAccountData nombaData = nombaClient.createVirtualAccount(
                creds, Mapper.toNombaRequest(customer, request.bvn()), env);

        // Persist the customer and its virtual account together in one short transaction.
        // TransactionTemplate goes through the transaction manager directly, avoiding the
        // self-invocation proxy pitfall of an @Transactional method called from this bean.
        return new TransactionTemplate(transactionManager).execute(_ -> {
            Customer saved = customerRepository.save(customer);
            VirtualAccount va = virtualAccountRepository.save(
                    Mapper.toVirtualAccount(merchant, saved, nombaData, env));
            log.info("Created customer {} for merchant {} with virtual account {}",
                    saved.getReference(), merchantId, va.getAccountNumber());
            return Mapper.toCustomerResponse(saved, va);
        });
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByReference(UUID merchantId, String reference) {
        Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));
        return Mapper.toCustomerResponse(customer, va);
    }

    /**
     * A customer's identity summary plus their paginated inbound-transaction history, scoped to
     * {@code merchantId} so one merchant can never read another merchant's customer data.
     */
    @Transactional(readOnly = true)
    public CustomerStatementResponse getStatement(UUID merchantId, String reference, Pageable pageable) {
        Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));

        Page<Transaction> transactions =
                transactionRepository.findByCustomerIdOrderByReceivedAtDesc(customer.getId(), pageable);
        var lifetimeKobo = transactionRepository.sumAmountByCustomerAndStatus(
                customer.getId(), TransactionStatus.SUCCESSFUL);

        return new CustomerStatementResponse(
                Mapper.toCustomerResponse(customer, va),
                lifetimeKobo,
                transactions.map(tx -> new StatementRowResponse(
                        tx.getReceivedAt(),
                        tx.getPayerName(),
                        tx.getProviderTransactionId(),
                        tx.getMatchStatus().name(),
                        tx.getAmount())));
    }

    /**
     * Partial update of the customer's own profile fields — {@code reference} and any KYC/status
     * field are never touched here (dedicated endpoints exist for those). When {@code firstName} or
     * {@code lastName} changes, the virtual account's Nomba-side bank account name (derived the same
     * way as at creation — see {@code Mapper.toNombaRequest}) is renamed to match via
     * {@code PUT /v1/accounts/virtual/{accountRef}}, keeping the two in sync; email/phoneNumber-only
     * changes never call Nomba at all.
     *
     * <p>NOT_SUPPORTED opts this method out of a wrapping transaction so the Nomba rename call runs
     * with no DB transaction open, per the provider-call convention — and must succeed before any
     * local field is persisted, so a failed Nomba call leaves the customer completely untouched
     * rather than a mismatched state where Cyrus's profile says one name and Nomba's VA says another.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerResponse rename(UUID merchantId, String reference, UpdateCustomerRequest request) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        CurrentProfile current = readTx.execute(_ -> {
            Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
            VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));
            return new CurrentProfile(customer.getReference(), customer.getFirstName(), customer.getLastName(), va.getEnvironment());
        });

        String newAccountName = null;
        if (request.firstName() != null || request.lastName() != null) {
            String firstName = request.firstName() != null ? request.firstName() : current.firstName();
            String lastName = request.lastName() != null ? request.lastName() : current.lastName();
            newAccountName = (lastName != null && !lastName.isBlank()) ? firstName + " " + lastName : firstName;

            NombaCredentials creds = merchantService.getNombaCredentials(merchantId);
            nombaClient.updateVirtualAccountName(creds, current.reference(), newAccountName, current.environment());
        }

        String finalAccountName = newAccountName;
        return new TransactionTemplate(transactionManager).execute(_ -> {
            Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
            VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));

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

    private record CurrentProfile(String reference, String firstName, String lastName, Environment environment) {}

    /**
     * Sets the customer's KYC tier. Cyrus doesn't verify KYC — that's the merchant's own process —
     * so this is an unguarded set, callable whenever the merchant's own verification completes.
     * The virtual account is completely untouched by a tier change.
     */
    @Transactional
    public CustomerResponse updateKycTier(UUID merchantId, String reference, KycTier tier) {
        Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        customer.setKycTier(tier);

        VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));
        log.info("Set KYC tier {} for customer {} (merchant {})", tier, reference, merchantId);
        return Mapper.toCustomerResponse(customer, va);
    }

    /**
     * Sets the customer's status, cascading to their (1:1) virtual account. ACTIVE/SUSPENDED are
     * freely reversible and Cyrus-local only. CLOSED is terminal (the soft-delete state) — the row,
     * VA, and full transaction history stay intact and queryable, but no further transition is
     * accepted once closed — AND permanently expires the virtual account on Nomba's side
     * ({@code DELETE /v1/accounts/virtual/{accountRef}}), so closing for good actually stops the
     * NUBAN from accepting transfers at all, not just locally. A payment landing on a non-ACTIVE VA
     * (e.g. the brief window before Nomba's expiry takes effect, or a SUSPENDED VA which Nomba still
     * considers live) is never attributed to the customer; see
     * {@link TransactionIngestionService#ingest} for that behavior.
     *
     * <p>NOT_SUPPORTED opts this method out of a wrapping transaction so the Nomba expiry call (only
     * made when closing) runs with no DB transaction open, per the provider-call convention — the
     * expiry must succeed before the local CLOSED status is ever persisted, so a failed expiry
     * leaves the customer untouched (still whatever it was before) rather than in a state where
     * Cyrus thinks it's closed but Nomba's VA is still live.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CustomerResponse updateStatus(UUID merchantId, String reference, CustomerStatus status) {
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        CurrentCustomerState current = readTx.execute(_ -> {
            Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
            if (customer.getStatus() == CustomerStatus.CLOSED) {
                throw new InvalidCustomerStateException("Customer is CLOSED — no further status changes are accepted");
            }
            VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));
            return new CurrentCustomerState(customer.getReference(), va.getEnvironment());
        });

        if (status == CustomerStatus.CLOSED) {
            NombaCredentials creds = merchantService.getNombaCredentials(merchantId);
            nombaClient.expireVirtualAccount(creds, current.reference(), current.environment());
        }

        return new TransactionTemplate(transactionManager).execute(_ -> {
            Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                    .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
            VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));

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

    private record CurrentCustomerState(String reference, Environment environment) {}
}
