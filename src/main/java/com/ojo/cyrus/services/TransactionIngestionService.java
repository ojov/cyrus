package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.dto.CyrusPaymentEvent;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.PaymentEvent;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.repositories.PaymentEventRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Records a Nomba payment event and attributes it to a customer's virtual account. Idempotent and
 * atomic: the raw event and the derived transaction are persisted in one transaction.
 *
 * <p>Failure handling is tuned for Nomba's retry policy (it retries any non-2xx):
 * <ul>
 *   <li>Duplicate delivery (by requestId) or duplicate transaction → recorded/ignored, no error.</li>
 *   <li>Orphan payment (no matching virtual account) → recorded as {@code IGNORED} for reconciliation;
 *       we do NOT throw, because retrying will not create the account.</li>
 *   <li>Transient failures (e.g. DB) propagate → non-2xx → Nomba retries.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {

    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Transactional
    public void ingest(CyrusPaymentEvent event, String rawPayload) {
        // 1. Event-level idempotency — same webhook delivered twice (Nomba retries).
        if (paymentEventRepository.existsByRequestId(event.getRequestId())) {
            log.info("Duplicate Nomba webhook requestId={}, skipping", event.getRequestId());
            return;
        }

        PaymentEvent paymentEvent = PaymentEvent.builder()
                .requestId(event.getRequestId())
                .provider(event.getProvider())
                .eventType(event.getEventType())
                .payload(rawPayload)
                .status(EventStatus.PENDING)
                .build();

        // 2. Relevance gate — only successful VA credits become transactions. Failures and non-VA
        //    events (e.g. POS purchases) are recorded for audit but never minted as a transaction.
        if (!event.isVirtualAccountCredit()) {
            paymentEvent.setStatus(EventStatus.IGNORED);
            paymentEvent.setStatusDetails("Non-credit event: " + event.getEventType());
            paymentEventRepository.save(paymentEvent);
            log.info("Ignoring non-credit Nomba event requestId={} type={}",
                    event.getRequestId(), event.getEventType());
            return;
        }

        // 3. Transaction-level idempotency — same underlying transfer seen before.
        if (transactionRepository.existsByProviderAndProviderTransactionId(
                event.getProvider(), event.getProviderTransactionId())) {
            paymentEvent.setStatus(EventStatus.IGNORED);
            paymentEvent.setStatusDetails("Duplicate transaction " + event.getProviderTransactionId());
            paymentEventRepository.save(paymentEvent);
            return;
        }

        // 4. Attribute to a virtual account. Unknown VA = orphan payment: record, don't retry.
        Optional<VirtualAccount> vaOpt =
                virtualAccountRepository.findByAccountNumber(event.getVirtualAccountNumber());
        if (vaOpt.isEmpty()) {
            paymentEvent.setStatus(EventStatus.IGNORED);
            paymentEvent.setStatusDetails("No virtual account for " + event.getVirtualAccountNumber());
            paymentEventRepository.save(paymentEvent);
            log.warn("Orphan Nomba payment: no VA for accountNumber={} (tx {})",
                    event.getVirtualAccountNumber(), event.getProviderTransactionId());
            return;
        }

        // 5. Record the transaction, attributed to the customer.
        VirtualAccount va = vaOpt.get();
        Customer customer = va.getCustomer();
        CyrusPaymentEvent.Payer payer = event.getPayer();

        Transaction tx = Transaction.builder()
                .merchant(customer.getMerchant())
                .customer(customer)
                .virtualAccount(va)
                .provider(event.getProvider())
                .providerTransactionId(event.getProviderTransactionId())
                .sessionId(event.getSessionId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .payerName(payer != null ? payer.getName() : null)
                .payerAccountNumber(payer != null ? payer.getAccountNumber() : null)
                .payerBank(payer != null ? payer.getBankName() : null)
                .status(TransactionStatus.SUCCESSFUL)
                .receivedAt(event.getEventTime())
                .rawPayload(rawPayload)
                .build();
        transactionRepository.save(tx);

        paymentEvent.setStatus(EventStatus.PROCESSED);
        paymentEventRepository.save(paymentEvent);

        log.info("Ingested Nomba payment tx {} for customer {} on VA {}",
                event.getProviderTransactionId(), customer.getReference(), va.getAccountNumber());
    }
}
