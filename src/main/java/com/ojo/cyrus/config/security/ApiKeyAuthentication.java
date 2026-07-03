package com.ojo.cyrus.config.security;

import com.ojo.cyrus.models.entities.Merchant;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {
    private final Merchant merchant;
    private final String apiKey;

    public ApiKeyAuthentication(Merchant merchant, String apiKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.merchant = merchant;
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return merchant;
    }

    @Override
    public String getName() {
        // The principal is a lazy Merchant proxy. The default getName() calls principal.toString(),
        // which Hibernate intercepts to initialize the proxy — but Spring calls getName() after the
        // request (publishRequestHandledEvent), when the session is closed → LazyInitializationException
        // that clobbers the real response into a 500. The id getter is proxy-safe (no initialization).
        return merchant == null ? "" : String.valueOf(merchant.getId());
    }
}
