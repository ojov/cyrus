package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantWebhookEventType;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.models.WebhookConfig;
import com.ojo.cyrus.models.entities.Customer;
import com.ojo.cyrus.models.entities.MerchantWebhookEvent;
import com.ojo.cyrus.models.entities.Transaction;
import com.ojo.cyrus.models.entities.VirtualAccount;
import com.ojo.cyrus.repositories.MerchantWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates the outbox record for an outbound merchant webhook and schedules its delivery. Called
 * from the transaction-state transition sites (SUCCESSFUL / REVERSED / MANUAL_REVIEW) while their
 * transaction is still open, so the {@link MerchantWebhookEvent} row commits atomically with the
 * status change. The JobRunr dispatch job is enqueued from an {@code afterCommit} hook so it can
 * never fire against an uncommitted (or rolled-back) row — the same pattern as reconciliation
 * scheduling in {@link TransactionIngestionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantWebhookService {

    private final MerchantWebhookEventRepository webhookEventRepository;
    private final MerchantWebhookDispatcher dispatcher;
    private final JobScheduler jobScheduler;
    private final ObjectMapper objectMapper;

    /**
     * Records a PENDING webhook event for {@code tx} and schedules its delivery. No-op (nothing to
     * deliver) when the merchant has no webhook URL configured for the transaction's environment,
     * or when this (transaction, eventType) has already been recorded (idempotent).
     *
     * <p>Must be called within the transaction that sets the transaction's terminal state.
     */
    public void recordAndScheduleDispatch(Transaction tx, MerchantWebhookEventType type) {
        Environment env = tx.getEnvironment();
        WebhookConfig config = tx.getMerchant().getWebhookConfigs().get(env);
        if (config == null || config.url() == null || config.url().isBlank()) {
            log.debug("No {} webhook URL for merchant {} — skipping {} for tx {}",
                    env, tx.getMerchant().getId(), type.getWireName(), tx.getId());
            return;
        }

        if (webhookEventRepository.existsByTransactionIdAndEventType(tx.getId(), type.getWireName())) {
            return; // already recorded for this transaction + event type
        }

        MerchantWebhookEvent event = MerchantWebhookEvent.builder()
                .merchant(tx.getMerchant())
                .transaction(tx)
                .environment(env)
                .eventType(type.getWireName())
                .webhookUrl(config.url())
                .payload(buildPayload(tx, type))
                .status(MerchantWebhookStatus.PENDING)
                .attempts(0)
                .build();
        try {
            webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // Two overlapping calls for the same (transaction, eventType) both passed the exists
            // check above before either committed — the loser hits the uk_webhook_transaction_event
            // constraint. The winner already recorded the outbox row, so this is a no-op, not a
            // failure (mirrors the same race handled for PaymentEvent in TransactionIngestionService).
            log.info("Concurrent insert won for tx={} eventType={} — skipping duplicate outbox row",
                    tx.getId(), type.getWireName());
            return;
        }

        UUID eventId = event.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                jobScheduler.schedule(MerchantWebhookDispatcher.initialJobId(eventId), Instant.now(),
                        () -> dispatcher.dispatch(eventId));
            }
        });

        log.info("Recorded {} webhook for tx {} (merchant {}) — dispatch scheduled",
                type.getWireName(), tx.getId(), tx.getMerchant().getId());
    }

    /**
     * Builds the JSON body. Money stays in kobo (integer minor units) — see the money convention;
     * the developer converts to naira at their display edge.
     */
    private String buildPayload(Transaction tx, MerchantWebhookEventType type) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("event", type.getWireName());
        root.put("createdAt", Instant.now().toString());

        ObjectNode data = root.putObject("data");
        data.put("transactionId", tx.getId().toString());
        data.put("amountKobo", tx.getAmount());
        if (tx.getFee() != null) {
            data.put("feeKobo", tx.getFee());
        } else {
            data.putNull("feeKobo");
        }
        data.put("currency", tx.getCurrency());
        data.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
        data.put("matchStatus", tx.getMatchStatus() != null ? tx.getMatchStatus().name() : null);
        data.put("sessionId", tx.getSessionId());
        data.put("providerTransactionId", tx.getProviderTransactionId());

        Customer customer = tx.getCustomer();
        data.put("customerReference", customer != null ? customer.getReference() : null);
        VirtualAccount va = tx.getVirtualAccount();
        data.put("virtualAccountNumber", va != null ? va.getAccountNumber() : null);

        data.put("paidAt", tx.getReceivedAt() != null ? tx.getReceivedAt().toString() : null);

        return objectMapper.writeValueAsString(root);
    }
}
