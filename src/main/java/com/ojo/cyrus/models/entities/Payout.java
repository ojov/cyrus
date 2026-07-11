package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.PayoutStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import com.ojo.cyrus.utils.MoneyUtil;

import java.math.BigDecimal;

/**
 * A merchant withdrawal from their Cyrus {@link Wallet} to a bank {@link Beneficiary}, executed via
 * Nomba's transfer API. Amounts are kobo at scale 4. The wallet is debited (with a matching
 * {@link LedgerEntry}) when the payout is initiated; a permanent failure returns the funds. The
 * paired {@code PAYOUT}-type {@link Transaction} carries this movement on the transaction ledger.
 */
@Entity
@Table(name = "payouts",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_reference", columnNames = {"reference"}),
        indexes = {
                @Index(name = "idx_payout_merchant", columnList = "merchant_id"),
                @Index(name = "idx_payout_status", columnList = "status")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    /** Cyrus-generated, developer-facing unique reference (also the idempotency key sent to Nomba). */
    @Column(nullable = false, unique = true)
    private String reference;

    /** Nomba's transfer identifier once accepted. */
    private String providerReference;

    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal amount; // kobo, scale 4 (whole-kobo valued — enforced at the API edge)

    @Column(precision = 38, scale = 4)
    @Builder.Default
    private BigDecimal fee = MoneyUtil.ZERO_KOBO; // kobo, scale 4

    @Column(length = 500)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    private String failureReason;
}
