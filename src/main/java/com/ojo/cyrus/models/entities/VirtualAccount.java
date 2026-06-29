package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.AccountStatus;
import jakarta.persistence.*;

import java.util.UUID;
@Entity
public class VirtualAccount {

    @Id
    @GeneratedValue
    private UUID id;


    @OneToOne(fetch = FetchType.LAZY)
    private EndUser customer;


    @Column(unique=true)
    private String accountRef;


    private String accountNumber;

    private String bankName;


    @Enumerated(EnumType.STRING)
    private AccountStatus status;
}
