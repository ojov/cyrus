package com.ojo.cyrus.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(

        @NotBlank(message = "app.base-url must be configured (APP_BASE_URL)")
        String baseUrl,

        @NotBlank(message = "app.frontend-url must be configured (APP_FRONTEND_URL)")
        String frontendUrl,

        @NotBlank(message = "app.encryption-key must be configured (APP_ENCRYPTION_KEY)")
        String encryptionKey,

        /**
         * Emails seeded as SUPER_ADMIN at startup (bootstrap admins). Comma-separated in
         * {@code APP_SUPER_ADMIN_EMAILS}; empty/absent means no bootstrap promotion. Additive only —
         * see {@code SuperAdminBootstrap}.
         */
        List<String> superAdminEmails,

        JwtConfig jwt

) {
    public AppProperties {
        superAdminEmails = superAdminEmails == null ? List.of() : superAdminEmails;
        // Fail fast at startup on a malformed encryption key, rather than 500-ing on the first
        // register that encrypts a Nomba secret. (@NotBlank above handles the missing case.)
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(encryptionKey.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "APP_ENCRYPTION_KEY is not valid Base64. Check for characters outside the Base64 " +
                        "alphabet — a '$' is a common sign the value was mangled by shell interpolation " +
                        "during deploy. Generate a fresh key with: openssl rand -base64 32", e);
            }
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalStateException(
                        "APP_ENCRYPTION_KEY must Base64-decode to 16, 24, or 32 bytes for AES (got " +
                        keyBytes.length + " bytes). Generate a 32-byte key with: openssl rand -base64 32");
            }
        }
    }

    public record JwtConfig(String issuer, int refreshExpiryDays) {}
}
