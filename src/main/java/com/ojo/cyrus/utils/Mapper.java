package com.ojo.cyrus.utils;

import com.ojo.cyrus.enums.CurrencyCode;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.MerchantCustomer;
import com.ojo.cyrus.models.entities.MerchantWebhookEvent;
import com.ojo.cyrus.models.entities.NombaPaymentEvent;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.PaymentEventListItem;
import com.ojo.cyrus.models.responses.PaymentEventResponse;
import com.ojo.cyrus.models.responses.WebhookDeliveryItem;
import com.ojo.cyrus.nomba.dto.NombaCreateVirtualAccountRequest;
import com.ojo.cyrus.nomba.dto.NombaVirtualAccountData;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class Mapper {

    public static MerchantCustomer toMerchantCustomer(Merchant merchant, CreateCustomerRequest request) {
        return MerchantCustomer.builder()
                .merchant(merchant)
                .externalCustomerId(request.reference())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .build();
    }

    /** Builds the Nomba VA-creation request. {@code accountRef} is the customer's externalCustomerId. */
    public static NombaCreateVirtualAccountRequest toNombaRequest(MerchantCustomer customer, String bvn) {
        return new NombaCreateVirtualAccountRequest(customer.getExternalCustomerId(), accountName(customer), bvn);
    }

    /** The bank-account holder name derived from the customer's name (first + last when present). */
    public static String accountName(MerchantCustomer customer) {
        return (customer.getLastName() != null && !customer.getLastName().isBlank())
                ? customer.getFirstName() + " " + customer.getLastName()
                : customer.getFirstName();
    }

    public static VirtualAccount toVirtualAccount(MerchantCustomer customer, NombaVirtualAccountData data,
                                                  Environment env) {
        return VirtualAccount.builder()
                .merchantCustomer(customer)
                .accountNumber(data.bankAccountNumber())
                .accountName(data.bankAccountName())
                .bankName(data.bankName())
                .currency(CurrencyCode.NGN)
                .providerReference(data.accountHolderId())
                .environment(env)
                .build();
    }

    public static CustomerResponse toCustomerResponse(MerchantCustomer customer, VirtualAccount va) {
        return new CustomerResponse(
                customer.getId(),
                customer.getExternalCustomerId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getStatus(),
                customer.getKycTier(),
                new CustomerResponse.VirtualAccountSummary(
                        va.getId(),
                        va.getAccountNumber(),
                        va.getAccountName(),
                        va.getBankName(),
                        va.getCurrency().name(),
                        va.getStatus().name()
                ),
                customer.getCreatedAt()
        );
    }

    public static Merchant mapToMerchantEntity(MerchantRegistrationRequest request) {
        return Merchant.builder()
                .businessName(request.businessName())
                .businessEmail(request.businessEmail())
                .passwordHash(request.password())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .build();
    }

    public static Transaction buildTransaction(NormalizedPaymentEvent event, String rawPayload,
                                               MerchantCustomer customer, VirtualAccount va,
                                               NormalizedPaymentEvent.Payer payer, NombaPaymentEvent paymentEvent) {
        return Transaction.builder()
                .merchant(customer.getMerchant())
                .customer(customer)
                .virtualAccount(va)
                .type(TransactionType.CUSTOMER_PAYMENT)
                .reference(generateReference("txn"))
                .providerTransactionId(event.getProviderTransactionId())
                .requestId(paymentEvent.getRequestId())
                .sessionId(event.getSessionId())
                .amount(event.getAmount())
                .fee(event.getFee())
                .currency(CurrencyCode.NGN)
                .environment(va.getEnvironment())
                .payerName(payer != null ? payer.getName() : null)
                .payerAccountNumber(payer != null ? payer.getAccountNumber() : null)
                .payerBank(payer != null ? payer.getBankName() : null)
                .paymentEvent(paymentEvent)
                .matchStatus(MatchStatus.UNMATCHED)
                // Webhooks are notifications, not proof — Nomba's own requery endpoint
                // (ReconciliationService) is the source of truth that promotes this to SUCCESSFUL.
                .status(TransactionStatus.PENDING)
                .receivedAt(event.getEventTime())
                .rawPayload(rawPayload)
                .build();
    }

    /** A short, unique, developer-facing reference, e.g. {@code txn_a1b2c3...}. */
    public static String generateReference(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static WebhookDeliveryItem toWebhookDeliveryItem(MerchantWebhookEvent event) {
        // event.getTransaction().getId() reads the FK off the lazy proxy without initializing it.
        Transaction tx = event.getTransaction();
        return new WebhookDeliveryItem(
                event.getId(),
                tx != null ? tx.getId() : null,
                event.getEnvironment(),
                event.getEventType(),
                event.getStatus(),
                event.getWebhookUrl(),
                event.getAttempts(),
                event.getLastResponseCode(),
                event.getLastError(),
                event.getNextRetryAt(),
                event.getDeliveredAt(),
                event.getCreatedAt());
    }

    public static PaymentEventListItem toPaymentEventListItem(NombaPaymentEvent event) {
        return new PaymentEventListItem(
                event.getId(),
                event.getRequestId(),
                event.getEventType(),
                event.getStatus(),
                event.getStatusDetails(),
                event.getCreatedAt());
    }

    public static PaymentEventResponse toPaymentEventResponse(NombaPaymentEvent event) {
        return new PaymentEventResponse(
                event.getId(),
                event.getRequestId(),
                event.getEventType(),
                event.getStatus(),
                event.getStatusDetails(),
                event.getRawPayload(),
                event.getCreatedAt());
    }
}
