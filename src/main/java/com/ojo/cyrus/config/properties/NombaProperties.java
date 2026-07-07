package com.ojo.cyrus.config.properties;

import com.ojo.cyrus.enums.Environment;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomba integration config. Cyrus operates a <em>single</em> Nomba account of its own — merchants
 * never supply Nomba credentials.
 *
 * <p>Nomba's {@code accountId} (parent account) and {@code subAccountId} are the SAME across sandbox
 * and production — only the {@code clientId}/{@code clientSecret} differ per environment. So those
 * two ids live at the top level (shared), and only the OAuth credentials are split into
 * {@code sandbox}/{@code live}, selected per request from the API key's environment.
 *
 * @param sandboxUrl    base URL for TEST (Nomba sandbox)
 * @param productionUrl base URL for LIVE (Nomba production)
 * @param timeoutMs     connect/read timeout for all Nomba HTTP calls
 * @param webhookSecret HMAC secret for verifying inbound Nomba webhook signatures
 * @param accountId     Nomba parent account id (shared across TEST/LIVE) — sent as the {@code accountId} header
 * @param subAccountId  optional sub-account id (shared across TEST/LIVE); when set, VAs are provisioned under it
 * @param sandbox       OAuth client credentials for TEST
 * @param live          OAuth client credentials for LIVE
 */
@ConfigurationProperties(prefix = "nomba")
public record NombaProperties(
        String sandboxUrl,
        String productionUrl,
        int timeoutMs,
        String webhookSecret,
        String accountId,
        String subAccountId,
        Credentials sandbox,
        Credentials live
) {

    /** Per-environment OAuth credentials — the only part of the config that changes between TEST and LIVE. */
    public record Credentials(String clientId, String clientSecret) {}

    /** The OAuth credential set for the given environment. */
    public Credentials credentials(Environment env) {
        return env == Environment.LIVE ? live : sandbox;
    }

    /** The Nomba base URL for the given environment. */
    public String baseUrl(Environment env) {
        return env == Environment.LIVE ? productionUrl : sandboxUrl;
    }
}
