package com.ojo.cyrus.models.entities;

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

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    @OneToOne(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private VirtualAccount virtualAccount;
}
