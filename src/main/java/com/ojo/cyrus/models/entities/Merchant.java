package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.models.BaseEntity;
import com.ojo.cyrus.models.WebhookConfig;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * A developer/business signed up on Cyrus. Merchants authenticate to the dashboard (JWT) and mint
 * API keys to provision customers + virtual accounts — all under <em>Cyrus's</em> single Nomba
 * account. Merchants therefore hold no Nomba credentials of their own; the only per-environment
 * config they own is the outbound webhook endpoint Cyrus posts payment/payout events to.
 */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Merchant extends BaseEntity {

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false, unique = true)
    private String businessEmail;

    private String businessType;

    private String phone;

    private String bankVerificationNumber;

    private String photoUrl;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MerchantStatus status = MerchantStatus.PENDING_VERIFICATION;

    /** Cyrus-level quota on how many virtual accounts this merchant may provision. */
    @Column(nullable = false)
    @Builder.Default
    private Integer virtualAccountLimit = 10;

    /**
     * Outbound-webhook config per environment (TEST/LIVE): a merchant registers a URL + Cyrus-generated
     * signing secret (stored encrypted) that Cyrus POSTs {@code payment.*}/{@code payout.*} events to.
     * LAZY — materialize inside a tx before use (open-in-view is disabled).
     */
    @ElementCollection
    @CollectionTable(name = "merchant_webhook_configs",
            joinColumns = @JoinColumn(name = "merchant_id"))
    @MapKeyColumn(name = "environment")
    @MapKeyEnumerated(EnumType.STRING)
    @Builder.Default
    private Map<Environment, WebhookConfig> webhookConfigs = new HashMap<>();
}
