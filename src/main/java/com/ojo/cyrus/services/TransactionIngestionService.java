package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.enums.MerchantCustomerStatus;
import com.ojo.cyrus.enums.MerchantWebhookEventType;
import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.ReconciliationFailureReason;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.exception.InvalidPaymentEventStateException;
import com.ojo.cyrus.models.dto.NormalizedPaymentEvent;
import com.ojo.cyrus.models.entities.MerchantCustomer;
import com.ojo.cyrus.models.entities.NombaPaymentEvent;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.nomba.NombaWebhookAdapter;
import com.ojo.cyrus.repositories.MerchantCustomerRepository;
import com.ojo.cyrus.repositories.TransactionRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import static com.ojo.cyrus.utils.Mapper.buildTransaction;

/**
 * Records a Nomba payment event and attributes it to a customer's virtual account. Idempotent and
 * atomic: the raw event and the derived transaction are persisted in one transaction.
 *
 * <p>Attribution is now purely by credited account number → {@link VirtualAccount} →
 * {@link MerchantCustomer} → merchant. There is a single Nomba account (Cyrus's), so there is no
 * per-merchant wallet-id resolution any more; an unknown account number simply has no owner. A known
 * VA that isn't {@link VirtualAccountStatus#ACTIVE} (suspended/closed customer) is treated the same
 * as an unknown one — recorded as an orphan, never silently attributed.
 *
 * <p>A webhook is a notification, not proof — a genuine VA credit is recorded as
 * {@link TransactionStatus#PENDING}, never {@code SUCCESSFUL}. Only {@link ReconciliationService},
 * via Nomba's own requery endpoint, promotes it to {@code SUCCESSFUL} and credits the merchant wallet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionIngestionService {
    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final MerchantCustomerRepository merchantCustomerRepository;
    private final PaymentEventService paymentEventService;
    private final NombaWebhookAdapter nombaAdapter;
    private final ReconciliationService reconciliationService;
    private final MerchantWebhookService merchantWebhookService;
    private final LedgerService ledgerService;

    /**
     * @return the created {@link Transaction} only when this event is a genuine VA credit —
     *         {@link Optional#empty()} for reversals, failures, non-credit events, orphans, and
     *         duplicates, none of which have a transaction to reconcile.
     */
    @Transactional
    public Optional<Transaction> ingest(NormalizedPaymentEvent event, String rawPayload) {
        // 1. Event-level idempotency — find or create atomically. Two concurrent deliveries of the
        //    same requestId can both miss the lookup, so we catch the unique-constraint violation on
        //    insert and re-fetch the winner's row.
        NombaPaymentEvent paymentEvent;
        try {
            paymentEvent = paymentEventService.findByRequestId(event.getRequestId())
                    .orElseGet(() -> paymentEventService.recordEvent(event, rawPayload));
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent insert won for requestId={}, re-fetching", event.getRequestId());
            paymentEvent = paymentEventService.findByRequestId(event.getRequestId())
                    .orElseThrow(() -> new IllegalStateException(
                            "PaymentEvent not found after concurrent insert for requestId=" + event.getRequestId()));
        }

        // 2. Reversal — flip the original transaction's status (and refund the wallet if it was
        //    already credited), rather than filing it away as a generic non-credit event.
        if (event.isReversal()) {
            handleReversal(event, paymentEvent);
            return Optional.empty();
        }

        // 3. Failure — a payment attempt that never credited us. Distinguished from IGNORED so it
        //    stays visible in reconciliation/exception views.
        if (event.isFailure()) {
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.FAILED,
                    ReconciliationFailureReason.NON_CREDIT_EVENT, "Payment failed: " + event.getEventType());
            log.info("Recording failed Nomba payment requestId={} tx={}",
                    event.getRequestId(), event.getProviderTransactionId());
            return Optional.empty();
        }

        // 4. Relevance gate — only successful VA credits become transactions.
        if (!event.isVirtualAccountCredit()) {
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.IGNORED,
                    ReconciliationFailureReason.NON_CREDIT_EVENT, "Non-credit event: " + event.getEventType());
            log.info("Ignoring non-credit Nomba event requestId={} type={}",
                    event.getRequestId(), event.getEventType());
            return Optional.empty();
        }

        // 5. Transaction-level idempotency — same underlying transfer seen before.
        if (transactionRepository.existsByProviderTransactionId(event.getProviderTransactionId())) {
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.PROCESSED_DUPLICATE,
                    ReconciliationFailureReason.DUPLICATE, "Duplicate transaction " + event.getProviderTransactionId());
            return Optional.empty();
        }

        // 6. Attribute to a virtual account. Unknown VA = orphan/misdirected payment: record, don't
        //    retry — visible (once the merchant is resolved) for manual reattribution.
        Optional<VirtualAccount> vaOpt =
                virtualAccountRepository.findByAccountNumber(event.getVirtualAccountNumber());
        if (vaOpt.isEmpty()) {
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.IGNORED,
                    ReconciliationFailureReason.UNKNOWN_VIRTUAL_ACCOUNT,
                    "No virtual account for " + event.getVirtualAccountNumber());
            log.warn("Orphan Nomba payment: no VA for accountNumber={} (tx {})",
                    event.getVirtualAccountNumber(), event.getProviderTransactionId());
            return Optional.empty();
        }

        VirtualAccount va = vaOpt.get();
        MerchantCustomer customer = va.getMerchantCustomer();

        // The VA's owning merchant is authoritative — attach it (+ VA) to the event so an orphan
        // recovered here is scoped correctly and visible to the merchant.
        paymentEvent.setMerchant(customer.getMerchant());
        paymentEvent.setVirtualAccount(va);
        paymentEvent.setCustomerReference(customer.getExternalCustomerId());

        // 7. A suspended/closed customer's VA still technically exists on Nomba's side (Nomba has no
        //    concept of "closed" until the expiry call runs — this is Cyrus's own status), but the
        //    money must never be silently attributed to an inactive customer. Record it as an orphan
        //    instead — same bucket as an unknown VA — so it's neither lost nor mis-credited; the
        //    merchant can reattribute it (once reactivated, or elsewhere) via the same flow.
        if (va.getStatus() != VirtualAccountStatus.ACTIVE) {
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.IGNORED,
                    ReconciliationFailureReason.INACTIVE_CUSTOMER,
                    "Virtual account " + va.getAccountNumber() + " is " + va.getStatus()
                            + " — payment not attributed to customer " + customer.getExternalCustomerId());
            log.warn("Payment landed on non-ACTIVE VA {} ({}) for customer {} — recorded as orphan",
                    va.getAccountNumber(), va.getStatus(), customer.getExternalCustomerId());
            return Optional.empty();
        }

        // 8. Record the transaction, attributed to the customer (PENDING — wallet credit happens at
        //    reconciliation, when Nomba confirms the transfer).
        Transaction tx = transactionRepository.save(
                buildTransaction(event, rawPayload, customer, va, event.getPayer(), paymentEvent));
        paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.PROCESSED, null);

        scheduleReconciliation(tx);

        log.info("Ingested Nomba payment tx {} for customer {} on VA {}",
                event.getProviderTransactionId(), customer.getExternalCustomerId(), va.getAccountNumber());
        return Optional.of(tx);
    }

    /**
     * Triggers reconciliation for a freshly-minted transaction as an afterCommit hook, so the async
     * requery only fires once the row is durably committed — {@link ReconciliationService#reconcileAsync}
     * runs it immediately off the ingesting request thread; anything it can't resolve right away
     * (Nomba not yet confirming, or the async attempt itself failing) is picked up by the periodic
     * sweep instead.
     */
    private void scheduleReconciliation(Transaction tx) {
        UUID transactionId = tx.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reconciliationService.reconcileAsync(transactionId);
            }
        });
    }

    /**
     * Flips the original transaction to REVERSED and, if it had already been credited to the
     * merchant wallet (i.e. it was SUCCESSFUL), refunds that credit with a REVERSAL ledger entry.
     * Matching is defensive: providerTransactionId first, then sessionId.
     */
    private void handleReversal(NormalizedPaymentEvent event, NombaPaymentEvent paymentEvent) {
        Optional<Transaction> original =
                transactionRepository.findByProviderTransactionId(event.getProviderTransactionId());

        if (original.isEmpty() && event.getSessionId() != null && !event.getSessionId().isBlank()) {
            original = transactionRepository.findBySessionId(event.getSessionId());
        }

        if (original.isEmpty()) {
            paymentEvent.setMerchant(null);
            paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.IGNORED,
                    ReconciliationFailureReason.UNKNOWN_VIRTUAL_ACCOUNT,
                    "Reversal for unknown transaction " + event.getProviderTransactionId());
            log.warn("Unmatched Nomba reversal: no transaction for tx={} session={}",
                    event.getProviderTransactionId(), event.getSessionId());
            return;
        }

        Transaction tx = original.get();
        boolean wasCredited = tx.getStatus() == TransactionStatus.SUCCESSFUL;
        tx.setStatus(TransactionStatus.REVERSED);

        if (wasCredited) {
            // Only the NET amount (gross minus Nomba's fee and Cyrus's markup, both already debited
            // out at reconciliation) was ever added to the wallet — refund that, not the gross amount,
            // or the reversal would drain more than this payment actually put in.
            BigInteger netCredited = tx.getAmount()
                    .subtract(tx.getFee() != null ? tx.getFee() : BigInteger.ZERO)
                    .subtract(tx.getPlatformFeeKobo() != null ? tx.getPlatformFeeKobo() : BigInteger.ZERO);
            ledgerService.debit(tx.getMerchant(), netCredited, tx,
                    LedgerEntryType.REVERSAL, "Reversal of transaction " + tx.getReference());
        }

        paymentEvent.setMerchant(tx.getMerchant());
        paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.PROCESSED, null);
        merchantWebhookService.recordAndScheduleDispatch(tx, MerchantWebhookEventType.PAYMENT_REVERSED);
        log.info("Reversed Nomba payment tx {} (providerTransactionId={}, refunded={})",
                tx.getId(), tx.getProviderTransactionId(), wasCredited);
    }

    /**
     * Replays a specific event by triggering its ingestion again. Ownership-checked.
     */
    @Transactional
    public void replayEvent(UUID merchantId, UUID id) {
        NombaPaymentEvent event = paymentEventService.findByIdForMerchant(merchantId, id);
        log.info("Replaying payment event: {}", id);
        NormalizedPaymentEvent cyrusEvent = nombaAdapter.toCyrusEvent(event.getRawPayload());
        this.ingest(cyrusEvent, event.getRawPayload());
    }

    /**
     * Manually attributes an orphaned (IGNORED) payment event to one of the merchant's own
     * customers — for a misdirected payment whose true recipient is known but whose payload VA number
     * doesn't resolve. Mints a {@link Transaction} against the CHOSEN customer's virtual account and
     * feeds it into the same reconciliation pipeline as a normal ingestion.
     */
    @Transactional
    public Transaction reattribute(UUID merchantId, UUID paymentEventId, String customerReference) {
        NombaPaymentEvent paymentEvent = paymentEventService.findByIdForMerchant(merchantId, paymentEventId);
        if (paymentEvent.getStatus() != NombaPaymentEventStatus.IGNORED) {
            throw new InvalidPaymentEventStateException(
                    "Only an IGNORED (orphan) event can be reattributed — this event is " + paymentEvent.getStatus());
        }

        MerchantCustomer customer = merchantCustomerRepository
                .findByMerchantIdAndExternalCustomerId(merchantId, customerReference)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerReference));
        if (customer.getStatus() != MerchantCustomerStatus.ACTIVE) {
            throw new InvalidPaymentEventStateException(
                    "Cannot reattribute to customer " + customerReference + " — status is " + customer.getStatus());
        }
        VirtualAccount va = virtualAccountRepository.findByMerchantCustomerId(customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Virtual account not found for customer"));

        NormalizedPaymentEvent event = nombaAdapter.toCyrusEvent(paymentEvent.getRawPayload());

        if (transactionRepository.existsByProviderTransactionId(event.getProviderTransactionId())) {
            throw new InvalidPaymentEventStateException("A transaction already exists for this payment");
        }

        Transaction tx = transactionRepository.save(
                buildTransaction(event, paymentEvent.getRawPayload(), customer, va, event.getPayer(), paymentEvent));
        paymentEventService.updateStatus(paymentEvent.getId(), NombaPaymentEventStatus.REATTRIBUTED,
                "Manually reattributed to customer " + customerReference);

        scheduleReconciliation(tx);

        log.info("Reattributed payment event {} to customer {} (merchant {}) as tx {}",
                paymentEventId, customerReference, merchantId, tx.getId());
        return tx;
    }
}
