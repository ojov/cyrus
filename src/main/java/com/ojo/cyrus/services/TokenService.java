package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.enums.TokenType;
import com.ojo.cyrus.exception.InvalidTokenException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.RefreshToken;
import com.ojo.cyrus.models.entities.Token;
import com.ojo.cyrus.repositories.RefreshTokenRepository;
import com.ojo.cyrus.repositories.TokenRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final TokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties appProperties;

    public record TokenPair(String accessToken, String refreshToken) {}

    public String generateToken(Authentication authentication) {
        return generateToken(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" ")));
    }

    public String generateToken(String subject, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(appProperties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_EXPIRY))
                .subject(subject)
                .claim("scope", scope)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * Generates a token pair: a short-lived access token and a long-lived refresh token.
     * The refresh token is stored hashed in the database; the raw value is returned once.
     */
    public TokenPair generateTokenPair(Merchant merchant, String userAgent, String ipAddress) {
        String accessToken = generateToken(merchant.getBusinessEmail(), "ROLE_MERCHANT");

        String rawRefreshToken = CryptoUtil.randomToken(64);
        String hashedRefresh = CryptoUtil.sha256(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .merchant(merchant)
                .token(hashedRefresh)
                .expiresAt(Instant.now().plus(appProperties.jwt().refreshExpiryDays(), ChronoUnit.DAYS))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, rawRefreshToken);
    }

    /**
     * Rotates a refresh token: revokes the old one and issues a new pair.
     * Returns the new token pair (refresh token is set as a cookie by the controller).
     */
    @Transactional
    public TokenPair refreshAccessToken(String refreshTokenValue, String userAgent, String ipAddress) {
        String hashed = CryptoUtil.sha256(refreshTokenValue);
        RefreshToken oldRefreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(hashed)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (oldRefreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Refresh token expired");
        }

        oldRefreshToken.setRevoked(true);
        refreshTokenRepository.save(oldRefreshToken);

        return generateTokenPair(oldRefreshToken.getMerchant(), userAgent, ipAddress);
    }

    /** Revokes a refresh token (used during logout). */
    public void revokeRefreshToken(String refreshTokenValue) {
        String hashed = CryptoUtil.sha256(refreshTokenValue);
        refreshTokenRepository.findByTokenAndRevokedFalse(hashed).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /** Deletes all revoked or expired refresh tokens (scheduled cleanup). */
    public int cleanupExpiredRefreshTokens() {
        return refreshTokenRepository.deleteRevokedOrExpired();
    }

    /** Mints a single-use token of the given type, valid for {@code validity}. */
    public Token createToken(Merchant merchant, TokenType type, Duration validity) {
        Token token = Token.builder()
                .token(UUID.randomUUID().toString())
                .type(type)
                .merchant(merchant)
                .expiresAt(Instant.now().plus(validity))
                .build();
        return tokenRepository.save(token);
    }

    /** Validates a token value against the expected type, throwing on unknown/used/expired. */
    public Token validateToken(String tokenValue, TokenType type) {
        Token token = tokenRepository.findByTokenAndType(tokenValue, type)
                .orElseThrow(() -> new InvalidTokenException(invalidMessage(type)));
        if (token.isUsed()) {
            throw new InvalidTokenException(usedMessage(type));
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException(expiredMessage(type));
        }
        return token;
    }

    /** Marks every outstanding, unused token of this type for the merchant as used, before issuing a new one. */
    public void invalidateOutstanding(UUID merchantId, TokenType type) {
        List<Token> outstanding = tokenRepository.findByMerchantIdAndTypeAndUsedFalse(merchantId, type);
        outstanding.forEach(t -> t.setUsed(true));
    }

    private static String invalidMessage(TokenType type) {
        return type == TokenType.PASSWORD_RESET ? "Invalid or expired reset link" : "Invalid verification link";
    }

    private static String usedMessage(TokenType type) {
        return type == TokenType.PASSWORD_RESET
                ? "This reset link has already been used"
                : "This verification link has already been used";
    }

    private static String expiredMessage(TokenType type) {
        return type == TokenType.PASSWORD_RESET
                ? "Reset link has expired. Please request a new one"
                : "Verification link has expired. Please register again";
    }
}
