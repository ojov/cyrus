package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.nomba.CredentialMapper;
import com.ojo.cyrus.nomba.NombaClient;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import com.ojo.cyrus.repositories.CustomerRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final MerchantService merchantService;
    private final NombaClient nombaClient;
    private final CredentialMapper credentialMapper;

    public CustomerResponse create(UUID merchantId, Environment env, CreateCustomerRequest request) {
        if (customerRepository.existsByMerchantIdAndReference(merchantId, request.reference())) {
            throw new AlreadyExistsException("A customer with this reference already exists");
        }

        Merchant merchant = merchantService.findById(merchantId);

        Customer customer = customerRepository.save(Mapper.toCustomer(merchant, request));

        NombaVirtualAccountData nombaData = nombaClient.createVirtualAccount(
                credentialMapper.fromMerchant(merchant),
                Mapper.toNombaRequest(customer, request.bvn()),
                env);

        VirtualAccount va = virtualAccountRepository.save(Mapper.toVirtualAccount(merchant, customer, nombaData));

        log.info("Created customer {} with account {}", customer.getReference(), va.getAccountNumber());
        return Mapper.toCustomerResponse(customer, va);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByReference(UUID merchantId, String reference) {
        Customer customer = customerRepository.findByMerchantIdAndReference(merchantId, reference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        VirtualAccount va = virtualAccountRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));

        return Mapper.toCustomerResponse(customer, va);
    }
}
