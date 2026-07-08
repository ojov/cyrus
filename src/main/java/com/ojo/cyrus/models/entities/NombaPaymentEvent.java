package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.NombaPaymentEventStatus;
import com.ojo.cyrus.enums.NombaPaymentEventType;
import com.ojo.cyrus.enums.ReconciliationFailureReason;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * The idempotent, deduplicated record of a raw inbound Nomba webhook (idempotent by {@code requestId}).
 * Recorded for every event Cyrus receives — genuine VA credits become {@code PROCESSED} and mint a
 * {@link Transaction}; non-credit/orphan/duplicate events are stored with a {@code status} +
 * {@link ReconciliationFailureReason} but no transaction. {@code merchant}/{@code virtualAccount} are
 * set when the credited account number resolves to a known VA; both are null for an orphan payment.
 */
@Entity
@Table(name = "nomba_payment_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_npe_request_id", columnNames = {"request_id"}),
        indexes = {
                @Index(name = "idx_npe_session_id", columnList = "session_id"),
                @Index(name = "idx_npe_account_number", columnList = "account_number"),
                @Index(name = "idx_npe_status", columnList = "status")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NombaPaymentEvent extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NombaPaymentEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_account_id")
    private VirtualAccount virtualAccount;

    private String transactionId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "account_number")
    private String accountNumber;

    /** Nomba's aliasAccountReference — echoes the VA's accountRef (the customer's externalCustomerId). */
    private String customerReference;

    @Column(nullable = false)
    @Builder.Default
    private BigInteger amount = BigInteger.ZERO; // integer kobo

    @Builder.Default
    private BigInteger fee = BigInteger.ZERO; // integer kobo

    private String providerWalletId;

    @Column(length = 500)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NombaPaymentEventStatus status = NombaPaymentEventStatus.RECEIVED;

    @Enumerated(EnumType.STRING)
    private ReconciliationFailureReason failureReason;

    /** Human-readable detail for the current status (e.g. which account number was unknown). */
    private String statusDetails;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(length = 500)
    private String signature;
}
