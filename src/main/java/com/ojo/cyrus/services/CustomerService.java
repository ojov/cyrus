package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
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
}
