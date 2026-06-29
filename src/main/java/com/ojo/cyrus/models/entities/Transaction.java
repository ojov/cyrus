package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.MatchStatus;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        uniqueConstraints={
                @UniqueConstraint(columnNames="nombaTransactionId")
        }
)
public class Transaction {

    @Id
    @GeneratedValue
    private UUID id;


    @ManyToOne(fetch=FetchType.LAZY)
    private VirtualAccount virtualAccount;


    private String nombaTransactionId;

    private Long amount;


    @Enumerated(EnumType.STRING)
    private MatchStatus matchStatus;
}