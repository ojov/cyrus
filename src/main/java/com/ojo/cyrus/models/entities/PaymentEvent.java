package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.EventStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_event_request_id", columnNames = {"request_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentEvent extends BaseEntity {

    @Column(name = "request_id", nullable = false)
    private String requestId;

    // Which merchant this event belongs to — resolved via the payload's wallet id (independent of
    // virtual-account attribution) or, once a VA is matched, from the VA's owning merchant. Null
    // only for a payment whose wallet AND virtual account are both unrecognized (no merchant to
    // scope it to); those aren't visible to any merchant, only via direct ops access.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    private String statusDetails;
}