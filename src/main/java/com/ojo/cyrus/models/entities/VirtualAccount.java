package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.Provider;
import com.ojo.cyrus.enums.VirtualAccountStatus;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "virtual_accounts")
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    private String accountName;

    private String bankName;

    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status;

    // Which Nomba credential set (TEST/LIVE) this VA was provisioned under — reconciliation needs
    // this to know which merchant credentials to requery with. Defaults existing sandbox-only rows
    // to TEST since Flyway isn't wired up yet and this column back-fills via ddl-auto.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'TEST'")
    private Environment environment;
}
