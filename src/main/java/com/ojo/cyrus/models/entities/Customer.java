package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.CustomerStatus;
import com.ojo.cyrus.enums.KycTier;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers",
        uniqueConstraints = @UniqueConstraint(name = "uk_merchant_customer_reference",
                columnNames = {"merchant_id", "reference"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Getter
public class Customer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private String reference;

    @Setter
    @Column(nullable = false)
    private String firstName;

    @Setter
    private String lastName;

    @Setter
    private String email;

    @Setter
    private String phoneNumber;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'ACTIVE'")
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'LEVEL_1'")
    @Builder.Default
    private KycTier kycTier = KycTier.LEVEL_1;

    @OneToOne(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private VirtualAccount virtualAccount;
}
