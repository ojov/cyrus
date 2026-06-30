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
}
