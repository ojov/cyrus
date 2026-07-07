package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * One delivery attempt of a {@link MerchantWebhookEvent} — the per-attempt audit trail behind the
 * outbox row's aggregate status/retry counters. Append-only: each POST to the merchant's endpoint
 * records the attempt number and the HTTP response (or failure reason) it produced.
 */
@Entity
@Table(name = "webhook_deliveries",
        indexes = @Index(name = "idx_delivery_event", columnList = "webhook_event_id"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WebhookDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_event_id", nullable = false)
    private MerchantWebhookEvent webhookEvent;

    @Column(nullable = false)
    private Integer attemptNumber;

    private Integer responseStatus;

    @Lob
    private String responseBody;

    @Column(length = 1000)
    private String failureReason;
}
