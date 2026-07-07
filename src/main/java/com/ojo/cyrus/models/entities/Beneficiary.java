package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A bank account a merchant can pay out to. Registered per environment (a sandbox beneficiary is not
 * a live one). {@code providerBeneficiaryId} is Nomba's identifier once the account is verified/saved
 * on their side; a {@link Payout} references the beneficiary it settles to.
 */
@Entity
@Table(name = "beneficiaries",
        indexes = {
                @Index(name = "idx_beneficiary_merchant", columnList = "merchant_id"),
                @Index(name = "idx_beneficiary_account", columnList = "merchant_id, account_number")
        })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    /** Merchant-supplied nickname, e.g. "Main GTBank". */
    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 10)
    private String accountNumber;

    /** NIP bank code. */
    @Column(nullable = false, length = 10)
    private String bankCode;

    @Column(nullable = false)
    private String bankName;

    /** Identifier returned by Nomba after saving the beneficiary. */
    private String providerBeneficiaryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;
}
