package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.ApiKeyStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
@Table(name = "api_keys",
        indexes = {@Index(name = "idx_api_key_hash", columnList = "key_hash")})
public class ApiKey extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;
    @Column(name = "key_hash", nullable = false, unique = true, length = 255)
    private String keyHash;
    @Column(nullable = false, length = 100)
    private String prefix;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;
    private Instant lastUsedAt;
    @Enumerated(EnumType.STRING)
    Environment environment;
    private Instant expiresAt;
    private Instant revokedAt;

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke() {
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = Instant.now();
    }
}