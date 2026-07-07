package com.ojo.cyrus.config.security;

import com.ojo.cyrus.config.properties.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Resolves the dashboard JWT from the httpOnly {@code cyrus_token} cookie set at login/register
 * (see {@code AuthController}), falling back to the standard {@code Authorization: Bearer} header
 * so the API remains directly testable from Postman/curl without a browser session.
 */
@Component
@RequiredArgsConstructor
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final AuthCookieProperties cookieProperties;
    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieProperties.name().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
