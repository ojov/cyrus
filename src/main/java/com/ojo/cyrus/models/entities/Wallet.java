package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigInteger;

/**
 * A merchant's balance of funds settled into Cyrus's Nomba account, held in integer kobo — one wallet
 * per merchant. Mutated only via balanced {@link LedgerEntry} postings; {@code @Version} guards
 * concurrent credit/debit with optimistic locking.
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Wallet extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(nullable = false)
    @Builder.Default
    private BigInteger availableBalance = BigInteger.ZERO; // integer kobo

    @Version
    private Long version;
}
