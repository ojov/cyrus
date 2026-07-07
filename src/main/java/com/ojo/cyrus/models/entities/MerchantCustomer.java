package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.enums.MerchantCustomerStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A customer belonging to a merchant. Cyrus provisions a dedicated {@link VirtualAccount} for each
 * one under Cyrus's own Nomba account (merchants never hold Nomba credentials).
 *
 * <p>{@code externalCustomerId} is the merchant's stable identifier for this customer in their own
 * system and doubles as the Nomba {@code accountRef} sent at VA creation (echoed back on every
 * webhook as {@code aliasAccountReference}); it is unique per merchant and never reused.
 */
@Entity
@Table(name = "merchant_customers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_customer_external_id",
                columnNames = {"merchant_id", "external_customer_id"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MerchantCustomer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "external_customer_id", nullable = false)
    private String externalCustomerId;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantCustomerStatus status = MerchantCustomerStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private KycTier kycTier = KycTier.TIER_1;

    /** Optional JSON metadata supplied by the merchant at creation. */
    @Lob
    private String metadata;

    @OneToOne(mappedBy = "merchantCustomer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private VirtualAccount virtualAccount;
}
