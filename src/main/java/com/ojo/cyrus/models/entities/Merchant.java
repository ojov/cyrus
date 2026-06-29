package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.BaseEntity;
import com.ojo.cyrus.models.NombaCredential;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
@Setter
@Getter
@Table(name = "merchants")
public class Merchant extends BaseEntity {
    @Column(nullable = false)
    private String businessName;
    @Column(nullable = false)
    private String businessEmail;
    @ElementCollection
    @CollectionTable(name = "merchant_nomba_credentials",
            joinColumns = @JoinColumn(name = "merchant_id")
    )
    @MapKeyColumn(name = "environment")
    @MapKeyEnumerated(EnumType.STRING)
    @Builder.Default
    private Map<Environment, NombaCredential> nombaCredentials =
            new HashMap<>();
    @Column(nullable = false)
    private String nombaParentAccountId;
    @ElementCollection
    @Builder.Default
    private Set<String> nombaSubAccountIds = new HashSet<>();

    @OneToMany(mappedBy = "merchant")
    private List<EndUser> customers;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.ACTIVE;
}
