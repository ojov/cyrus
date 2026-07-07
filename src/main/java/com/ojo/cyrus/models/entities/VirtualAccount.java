package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.CurrencyCode;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A dedicated virtual account provisioned on Nomba (under Cyrus's own account) and bound 1:1 to a
 * {@link MerchantCustomer}. Incoming transfers to {@code accountNumber} are attributed to the owning
 * customer → merchant. {@code environment} records whether it was created under Cyrus's sandbox or
 * live Nomba credentials, so rename/expire/requery calls hit the right base URL + token.
 */
@Entity
@Table(name = "virtual_accounts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VirtualAccount extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_customer_id", nullable = false, unique = true)
    private MerchantCustomer merchantCustomer;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    private String accountName;

    private String bankName;

    /** Nomba's identifier for this virtual account. */
    @Column(unique = true)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CurrencyCode currency = CurrencyCode.NGN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;
}
