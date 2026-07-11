package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.LedgerEntryType;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * One leg of a double-entry posting against a merchant {@link Wallet}, tied to the {@link Transaction}
 * that caused it. {@code amount} is signed kobo at scale 4 — positive credits the wallet, negative
 * debits it — and the entries for a single transaction sum to the net balance change. Append-only:
 * the ledger is the immutable audit trail; the wallet balance is a materialized running total of it.
 */
@Entity
@Table(name = "ledger_entries",
        indexes = {
                @Index(name = "idx_ledger_transaction", columnList = "transaction_id"),
                @Index(name = "idx_ledger_wallet", columnList = "wallet_id"),
                @Index(name = "idx_ledger_type", columnList = "type")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /** Signed kobo (scale 4): positive = credit to the wallet, negative = debit. */
    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    @Column(length = 500)
    private String description;
}
