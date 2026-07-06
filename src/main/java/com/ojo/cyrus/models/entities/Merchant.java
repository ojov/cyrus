package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.BaseEntity;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.models.WebhookConfig;
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
    @Column(nullable = false, unique = true)
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
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING_VERIFICATION;

    // Outbound-webhook config per environment (TEST/LIVE), mirroring nombaCredentials: a merchant
    // registers a URL + Cyrus-generated signing secret (stored encrypted) that Cyrus POSTs
    // payment.* events to. LAZY — materialize inside a tx before use (see nombaCredentials).
    @ElementCollection
    @CollectionTable(name = "merchant_webhook_configs",
            joinColumns = @JoinColumn(name = "merchant_id")
    )
    @MapKeyColumn(name = "environment")
    @MapKeyEnumerated(EnumType.STRING)
    @Builder.Default
    private Map<Environment, WebhookConfig> webhookConfigs = new HashMap<>();
}
