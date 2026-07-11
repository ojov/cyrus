package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.PlatformProfitEntryType;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * One entry in the append-only platform profit ledger. Tracks the aggregate movement of funds
 * through Cyrus's Nomba account — the source of truth for "how much profit has Cyrus made?"
 *
 * <p>Entries are written in the same transaction as the merchant wallet posting they correspond to,
 * ensuring both ledgers stay consistent. The running total is derived via {@code SUM(amount_kobo)},
 * not a mutable balance column.
 */
@Entity
@Table(name = "platform_profit_entries",
        indexes = {
                @Index(name = "idx_ppe_transaction", columnList = "transaction_id"),
                @Index(name = "idx_ppe_payout", columnList = "payout_id"),
                @Index(name = "idx_ppe_type", columnList = "entry_type"),
                @Index(name = "idx_ppe_created", columnList = "created_at")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlatformProfitEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private Payout payout;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private PlatformProfitEntryType entryType;

    /**
     * Signed kobo at scale 4: positive = money flowing into the Nomba account (inflow),
     * negative = money flowing out (outflow). The running total is {@code SUM(amount_kobo)}.
     */
    @Column(name = "amount_kobo", nullable = false, precision = 38, scale = 4)
    private BigDecimal amountKobo;

    @Column(length = 500)
    private String description;
}
