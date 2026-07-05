package com.ojo.cyrus.models.entities;

import com.ojo.cyrus.enums.TokenType;
import com.ojo.cyrus.models.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A single-use, expiring token tied to a merchant — covers email verification and password reset.
 * {@link TokenType} keeps the two kinds separate; every lookup goes through it so a token minted for
 * one purpose can never be validated as the other.
 */
@Entity
@Table(name = "tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Token extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private boolean used = false;
}
