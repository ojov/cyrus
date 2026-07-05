package com.ojo.cyrus.utils;

import com.ojo.cyrus.enums.*;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.models.dto.CyrusPaymentEvent;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.nomba.dto.NombaCreateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Mapper {

    public static Customer toCustomer(Merchant merchant, CreateCustomerRequest request) {
        return Customer.builder()
                .merchant(merchant)
                .reference(request.reference())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .build();
    }

    public static NombaCreateVirtualAccountRequest toNombaRequest(Customer customer, String bvn) {
        String accountName = customer.getLastName() != null && !customer.getLastName().isBlank()
                ? customer.getFirstName() + " " + customer.getLastName()
                : customer.getFirstName();
        return new NombaCreateVirtualAccountRequest(customer.getReference(), accountName, bvn);
    }

    public static VirtualAccount toVirtualAccount(Merchant merchant, Customer customer, NombaVirtualAccountData data,
                                                   Environment env) {
        return VirtualAccount.builder()
                .merchant(merchant)
                .customer(customer)
                .accountNumber(data.bankAccountNumber())
                .accountName(data.bankAccountName())
                .bankName(data.bankName())
                .currency(data.currency())
                .provider(Provider.NOMBA)
                .providerReference(data.accountHolderId())
                .status(VirtualAccountStatus.ACTIVE)
                .environment(env)
                .build();
    }

    public static CustomerResponse toCustomerResponse(Customer customer, VirtualAccount va) {
        return new CustomerResponse(
                customer.getId(),
                customer.getReference(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                new CustomerResponse.VirtualAccountSummary(
                        va.getId(),
                        va.getAccountNumber(),
                        va.getAccountName(),
                        va.getBankName(),
                        va.getCurrency(),
                        va.getStatus().name()
                ),
                customer.getCreatedAt()
        );
    }

    public static Merchant mapToMerchantEntity(MerchantRegistrationRequest request) {
        Merchant merchant = Merchant.builder()
                .businessName(request.businessName())
                .businessEmail(request.businessEmail())
                .passwordHash(request.password())
                .nombaParentAccountId(request.nombaParentAccountId())
                .nombaSubAccountIds(request.subAccountIds())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .build();

        if (request.nombaClientId() != null) {
            merchant.getNombaCredentials().put(Environment.TEST,
                    new NombaCredential(request.nombaClientId(), request.nombaClientSecret()));
        }

        return merchant;
    }
    public static Transaction buildTransaction(CyrusPaymentEvent event, String rawPayload, Customer customer, VirtualAccount va, CyrusPaymentEvent.Payer payer) {
        Transaction tx = Transaction.builder()
                .merchant(customer.getMerchant())
                .customer(customer)
                .virtualAccount(va)
                .provider(event.getProvider())
                .providerTransactionId(event.getProviderTransactionId())
                .sessionId(event.getSessionId())
                .amount(event.getAmount())
                .fee(event.getFee())
                .currency(event.getCurrency())
                .environment(va.getEnvironment())
                .payerName(payer != null ? payer.getName() : null)
                .payerAccountNumber(payer != null ? payer.getAccountNumber() : null)
                .payerBank(payer != null ? payer.getBankName() : null)
                .matchStatus(MatchStatus.UNMATCHED)
                // Webhooks are notifications, not proof — Nomba's own requery endpoint
                // (ReconciliationService) is the source of truth that promotes this to SUCCESSFUL.
                .status(TransactionStatus.PENDING)
                .receivedAt(event.getEventTime())
                .rawPayload(rawPayload)
                .build();
        return tx;
    }
}
