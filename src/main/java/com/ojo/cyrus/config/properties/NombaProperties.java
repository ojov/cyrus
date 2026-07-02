package com.ojo.cyrus.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nomba")
public record NombaProperties(String sandboxUrl, String productionUrl, int timeoutMs) {}
