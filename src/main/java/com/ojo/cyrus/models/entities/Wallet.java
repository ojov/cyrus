package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import com.ojo.cyrus.utils.MoneyUtil;

import java.math.BigDecimal;

/**
 * A merchant's balance of funds settled into Cyrus's Nomba account, held in kobo at scale 4 — one
 * wallet per merchant. Mutated only via balanced {@link LedgerEntry} postings; {@code @Version}
 * guards concurrent credit/debit with optimistic locking.
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

    @Column(nullable = false, precision = 38, scale = 4)
    @Builder.Default
    private BigDecimal availableBalance = MoneyUtil.ZERO_KOBO; // kobo, scale 4

    @Version
    private Long version;
}
