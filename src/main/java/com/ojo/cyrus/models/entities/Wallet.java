package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * A merchant's balance of funds settled into Cyrus's Nomba account, held in integer kobo. Kept
 * per-environment — TEST (sandbox) money and LIVE money never mix — so a merchant has one wallet per
 * {@link Environment}. Mutated only via balanced {@link LedgerEntry} postings; {@code @Version}
 * guards concurrent credit/debit with optimistic locking.
 */
@Entity
@Table(name = "wallets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_merchant_environment",
                columnNames = {"merchant_id", "environment"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wallet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    @Column(nullable = false)
    @Builder.Default
    private BigInteger availableBalance = BigInteger.ZERO; // integer kobo

    @Version
    private Long version;
}
