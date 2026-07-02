package com.ojo.cyrus.utils;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.Merchant;
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

    public static VirtualAccount toVirtualAccount(Merchant merchant, Customer customer, NombaVirtualAccountData data) {
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
}
