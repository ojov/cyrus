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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Records a Nomba payment event and attributes it to a customer's virtual account. Idempotent and
 * atomic: the raw event and the derived transaction are persisted in one transaction.
 *
 * <p>Failure handling is tuned for Nomba's retry policy (exponential backoff, 5 retries):
 * <ul>
 *   <li>Duplicate delivery (by requestId) -> recorded/ignored, returns 2xx to stop retries.</li>
 *   <li>Duplicate transaction (by providerTransactionId) -> recorded/ignored, returns 2xx.</li>
 *   <li>Orphan payment (no matching virtual account) -> recorded as {@code IGNORED} for reconciliation;
 *       returns 2xx because retrying will not resolve the missing account.</li>
 *   <li>Transient failures (e.g. DB connection) propagate -> non-2xx -> Nomba retries.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {
    
    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentEventService paymentEventService;
    private final NombaWebhookAdapter nombaAdapter;

    @Transactional
    public void ingest(CyrusPaymentEvent event, String rawPayload) {
        // 1. Event-level idempotency — same webhook delivered twice (Nomba retries).
        // If replaying, we might want to bypass this check. 
        // For now, let's just find the existing one if it exists or record a new one.
        PaymentEvent paymentEvent;
        Optional<PaymentEvent> existing = paymentEventService.findByRequestId(event.getRequestId());
        
        if (existing.isPresent()) {
            paymentEvent = existing.get();
            log.info("Processing existing PaymentEvent requestId={}", event.getRequestId());
        } else {
            paymentEvent = paymentEventService.recordEvent(
                    event.getRequestId(),
                    event.getProvider(),
                    event.getEventType(),
                    rawPayload
            );
        }

        // 2. Relevance gate — only successful VA credits become transactions. Failures and non-VA
        //    events (e.g. POS purchases) are recorded for audit but never minted as a transaction.
        if (!event.isVirtualAccountCredit()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "Non-credit event: " + event.getEventType());
            log.info("Ignoring non-credit Nomba event requestId={} type={}",
                    event.getRequestId(), event.getEventType());
            return;
        }

        // 3. Transaction-level idempotency — same underlying transfer seen before.
        if (transactionRepository.existsByProviderAndProviderTransactionId(
                event.getProvider(), event.getProviderTransactionId())) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "Duplicate transaction " + event.getProviderTransactionId());
            return;
        }

        // 4. Attribute to a virtual account. Unknown VA = orphan payment: record, don't retry.
        Optional<VirtualAccount> vaOpt =
                virtualAccountRepository.findByAccountNumber(event.getVirtualAccountNumber());
        if (vaOpt.isEmpty()) {
            paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.IGNORED,
                    "No virtual account for " + event.getVirtualAccountNumber());
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
                .fee(event.getFee())
                .currency(event.getCurrency())
                .payerName(payer != null ? payer.getName() : null)
                .payerAccountNumber(payer != null ? payer.getAccountNumber() : null)
                .payerBank(payer != null ? payer.getBankName() : null)
                .status(TransactionStatus.SUCCESSFUL)
                .receivedAt(event.getEventTime())
                .rawPayload(rawPayload)
                .build();
        transactionRepository.save(tx);

        paymentEventService.updateStatus(paymentEvent.getId(), EventStatus.PROCESSED, null);

        log.info("Ingested Nomba payment tx {} for customer {} on VA {}",
                event.getProviderTransactionId(), customer.getReference(), va.getAccountNumber());
    }

    /**
     * Replays a specific event by triggering its ingestion again.
     */
    @Transactional
    public void replayEvent(UUID id) {
        PaymentEvent event = paymentEventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PaymentEvent not found: " + id));

        log.info("Replaying payment event: {}", id);

        if (event.getProvider() == com.ojo.cyrus.enums.Provider.NOMBA) {
            CyrusPaymentEvent cyrusEvent = nombaAdapter.toCyrusEvent(event.getPayload());
            this.ingest(cyrusEvent, event.getPayload());
        } else {
            throw new UnsupportedOperationException("Replay not implemented for provider: " + event.getProvider());
        }
    }
}
