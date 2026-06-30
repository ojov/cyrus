package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "virtual_accounts", uniqueConstraints = {@UniqueConstraint(name = "uk_merchant_customer_reference",
        columnNames = {"merchant_id", "customer_reference"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VirtualAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;
    @Column(nullable = false)
    private String customerReference;
    @Column(nullable = false)
    private String customerName;
    private String customerEmail;
    @Column(nullable = false, unique = true)
    private String accountNumber;
    private String accountName;
    private String bankName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status;
}
