package com.ojo.cyrus.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(

        @NotBlank(message = "app.base-url must be configured (APP_BASE_URL)")
        String baseUrl,

        @NotBlank(message = "app.encryption-key must be configured (APP_ENCRYPTION_KEY)")
        String encryptionKey,

        JwtConfig jwt

) {
    public record JwtConfig(String issuer, int expiryHours) {}
}
