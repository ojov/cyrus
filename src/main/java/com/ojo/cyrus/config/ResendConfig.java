package com.ojo.cyrus.config;


import com.ojo.cyrus.config.properties.ResendProperties;
import com.resend.Resend;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResendConfig {
    private final ResendProperties resendProperties;
    @Bean
    public Resend resendClient() {
        return new Resend(resendProperties.apiKey());
    }
}