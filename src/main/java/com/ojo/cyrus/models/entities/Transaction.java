package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.CurrencyCode;
import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A money movement on Cyrus's books: an inbound {@code CUSTOMER_PAYMENT} attributed to a customer's
 * virtual account, or an outbound {@code PAYOUT}. Amounts are kobo at scale 4 (sub-kobo precision).
 *
 * <p>The reconciliation fields ({@code sessionId}, {@code matchStatus}, {@code reconciliationAttempts},
 * …) drive the requery-based reconciliation engine and are the judged surface — they are retained
 * from the pre-refactor model. {@code environment} is denormalized so reconciliation can pick the
 * right Nomba credential set without touching the lazy VA association (open-in-view is disabled).
 */
@Entity
@Table(name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transaction_reference", columnNames = {"reference"}),
                @UniqueConstraint(name = "uk_transaction_provider_tx_id", columnNames = {"provider_transaction_id"})
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Transaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private MerchantCustomer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_account_id")
    private VirtualAccount virtualAccount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_event_id")
    private NombaPaymentEvent paymentEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TransactionType type = TransactionType.CUSTOMER_PAYMENT;

    /** Cyrus-generated, developer-facing unique reference for this transaction. */
    @Column(nullable = false, unique = true)
    private String reference;

    /** Nomba's transaction id — the dedup key for inbound payments (null for internal/pending payouts). */
    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    /** Webhook requestId from the source PaymentEvent (denormalized for direct lookup). */
    private String requestId;

    /** Nomba session id — used to reconcile/confirm via GET /v1/transactions/requery/{sessionId}. */
    private String sessionId;

    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal amount; // kobo, scale 4. For CUSTOMER_PAYMENT: gross amount
    // the payer sent. For PAYOUT: the transfer amount to the beneficiary — NOT the total wallet
    // debit (see Payout.fee for Cyrus's ₦30 flat fee, which is debited on top of this amount).

    /** Nomba's own confirmed fee (kobo) — set authoritatively at reconciliation via {@code fixedCharge}. */
    @Column(precision = 38, scale = 4)
    private BigDecimal fee;

    /**
     * Cyrus's markup-only fee (kobo): {@code fee * markupMultiplier - fee}. Together with {@code fee}
     * this is deducted from {@code amount} before crediting the merchant wallet — see
     * {@link com.ojo.cyrus.services.ReconciliationService}. Null until reconciliation confirms a fee.
     */
    @Column(precision = 38, scale = 4)
    private BigDecimal platformFeeKobo;

    /**
     * Merchant-facing total fee (kobo) — the fee actually charged to the merchant on this
     * transaction. For CUSTOMER_PAYMENT: {@code inflowPercent} of the gross amount, clamped to
     * {@code [inflowMinKobo, inflowMaxKobo]} (i.e. {@code fee + platformFeeKobo}). For PAYOUT:
     * Cyrus's flat {@code payoutFlatFeeKobo}. This is the value exposed in
     * {@link com.ojo.cyrus.models.responses.TransactionResponse#feeKobo()}.
     */
    @Column(precision = 38, scale = 4)
    private BigDecimal merchantFeeKobo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CurrencyCode currency = CurrencyCode.NGN;

    @Column(length = 500)
    private String narration;

    private String payerName;

    private String payerAccountNumber;

    private String payerBank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus matchStatus = MatchStatus.UNMATCHED;

    /** Why matchStatus is what it is — set by reconciliation (e.g. "Nomba reports 45000 kobo, we have 50000"). */
    private String matchStatusDetails;

    private Instant lastReconciledAt;

    /** How many times Nomba's requery came back "not found yet"; caps at reconciliation.max-attempts. */
    @Column(nullable = false)
    @Builder.Default
    private int reconciliationAttempts = 0;

    private Instant receivedAt;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Version
    private Long version;
}
