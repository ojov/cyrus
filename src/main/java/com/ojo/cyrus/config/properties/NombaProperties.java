package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomba integration config. Cyrus operates a single Nomba account of its own with one credential set
 * (there is no TEST/LIVE split — merchants get a single API key). {@code subAccountId} is optional:
 * when set, virtual accounts are provisioned under that sub-account.
 *
 * @param baseUrl       Nomba API base URL (e.g. https://api.nomba.com)
 * @param timeoutMs     connect/read timeout for all Nomba HTTP calls
 * @param webhookSecret HMAC secret for verifying inbound Nomba webhook signatures
 * @param clientId      OAuth client id
 * @param clientSecret  OAuth client secret
 * @param accountId     Nomba parent account id (sent as the {@code accountId} header)
 * @param subAccountId  optional sub-account id VAs are provisioned under
 */
@ConfigurationProperties(prefix = "nomba")
public record NombaProperties(
        String baseUrl,
        int timeoutMs,
        String webhookSecret,
        String clientId,
        String clientSecret,
        String accountId,
        String subAccountId
) {}
