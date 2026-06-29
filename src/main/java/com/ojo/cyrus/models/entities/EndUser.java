package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.KycTier;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name="end_users",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames={"merchant_id","external_ref"}
                )
        }
)
public class EndUser {

    @Id
    @GeneratedValue
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    private Merchant merchant;

    private String externalRef;

    private String displayName;

    @Enumerated(EnumType.STRING)
    private KycTier kycTier;
}