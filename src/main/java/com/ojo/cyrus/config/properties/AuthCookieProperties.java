package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The httpOnly cookie carrying the dashboard JWT. The frontend never reads or stores the raw
 * token — the browser sends it automatically on requests to this API, and only this API can read
 * it. {@code secure} must be true in production (HTTPS-only) but is relaxed to false for local
 * dev over plain http (see {@code application.yaml} vs {@code application-prod.yml}).
 */
@ConfigurationProperties(prefix = "app.auth-cookie")
public record AuthCookieProperties(
        @DefaultValue("cyrus_token") String name,
        @DefaultValue("false") boolean secure
) {}
