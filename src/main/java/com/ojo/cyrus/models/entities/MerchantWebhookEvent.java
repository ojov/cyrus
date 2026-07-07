package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Outbox record for one outbound webhook delivery to a merchant. Created (PENDING) in the same
 * transaction that sets a transaction's terminal state, then delivered by
 * {@link com.ojo.cyrus.services.MerchantWebhookDispatcher} via a JobRunr job. The unique constraint
 * on (transaction, event_type) makes outbox creation idempotent — one event per transaction per type.
 */
@Entity
@Table(name = "merchant_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_transaction_event",
                        columnNames = {"transaction_id", "event_type"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@Setter
@Getter
public class MerchantWebhookEvent extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    // The wire name of the event, e.g. "payment.succeeded" (MerchantWebhookEventType.wireName()).
    @Column(name = "event_type")
    private String eventType;

    // The target URL, snapshotted at creation time so an in-flight delivery keeps its target even
    // if the merchant later changes their registered URL.
    private String webhookUrl;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MerchantWebhookStatus status = MerchantWebhookStatus.PENDING;

    @Builder.Default
    private int attempts = 0;

    private Integer lastResponseCode;

    private String lastError;

    private Instant nextRetryAt;

    private Instant deliveredAt;
}
