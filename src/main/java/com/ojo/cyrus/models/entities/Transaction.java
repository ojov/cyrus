package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigInteger;
import java.time.Instant;

@Entity
@Table(name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provider_transaction_id", columnNames = {"provider", "provider_transaction_id"}),
                @UniqueConstraint(name = "uk_transaction_request_id", columnNames = {"request_id"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_account_id")
    private VirtualAccount virtualAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Column(name = "provider_transaction_id", nullable = false)
    private String providerTransactionId;

    // Webhook requestId from PaymentEvent (denormalized for direct query lookup).
    @Column(name = "request_id", nullable = false)
    private String requestId;

    // Nomba session id — used to reconcile/confirm via GET /v1/transactions/requery/{sessionId}.
    private String sessionId;

    @Column(nullable = false)
    private BigInteger amount; // integer kobo (minor units)

    private BigInteger fee; // integer kobo (minor units)

    @Column(nullable = false)
    private String currency;

    // Denormalized from the virtual account at ingestion time (not read via the lazy
    // `virtualAccount` association) so ReconciliationService can pick the right Nomba credential
    // set without touching a lazy proxy outside a transaction (open-in-view is disabled).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'TEST'")
    private Environment environment;

    private String payerName;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id", nullable = false)
    private PaymentEvent paymentEvent;

    private String payerAccountNumber;

    private String payerBank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    // Why matchStatus is what it is — set by ReconciliationService (e.g. "Nomba reports 45000
    // kobo, we have 50000"), same pattern as PaymentEvent.statusDetails.
    private String matchStatusDetails;

    // When ReconciliationService last requeried this transaction against Nomba. Null until the
    // first reconciliation sweep picks it up.
    private Instant lastReconciledAt;

    // How many times Nomba's requery has come back "not found yet" for this transaction. Drives
    // the backoff/give-up decision in ReconciliationService — capped at reconciliation.max-attempts
    // before the transaction is flagged MANUAL_REVIEW instead of retried forever.
    @Column(nullable = false)
    @Builder.Default
    private int reconciliationAttempts = 0;

    private Instant receivedAt;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;
}