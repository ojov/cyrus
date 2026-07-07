package com.ojo.cyrus.config.properties;

import com.ojo.cyrus.enums.Environment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomba integration config. Cyrus now operates a <em>single</em> Nomba account of its own — merchants
 * never supply Nomba credentials — so the platform credentials live here (not on {@code Merchant}),
 * one set per {@link Environment}: {@code sandbox} for TEST, {@code live} for LIVE. The correct set +
 * base URL is selected per request from the API key's environment.
 *
 * @param sandboxUrl    base URL for TEST (Nomba sandbox)
 * @param productionUrl base URL for LIVE (Nomba production)
 * @param timeoutMs     connect/read timeout for all Nomba HTTP calls
 * @param webhookSecret HMAC secret for verifying inbound Nomba webhook signatures
 * @param sandbox       platform Nomba credentials used for TEST
 * @param live          platform Nomba credentials used for LIVE
 */
@ConfigurationProperties(prefix = "nomba")
public record NombaProperties(
        String sandboxUrl,
        String productionUrl,
        int timeoutMs,
        String webhookSecret,
        Credentials sandbox,
        Credentials live
) {

    /**
     * One platform credential set. {@code subAccountId} is optional: when set, virtual accounts are
     * provisioned under that sub-account (POST /v1/accounts/virtual/{subAccountId}); when null, they
     * are created directly under the parent account.
     */
    public record Credentials(String clientId, String clientSecret, String accountId, String subAccountId) {}

    /** The credential set for the given environment. */
    public Credentials credentials(Environment env) {
        return env == Environment.LIVE ? live : sandbox;
    }

    /** The Nomba base URL for the given environment. */
    public String baseUrl(Environment env) {
        return env == Environment.LIVE ? productionUrl : sandboxUrl;
    }
}
