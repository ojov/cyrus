package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.ApiKeyStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.entities.ApiKey;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.GeneratedApiKeyResponse;
import com.ojo.cyrus.repositories.ApiKeyRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;

    public GeneratedApiKeyResponse createApiKey(Merchant merchant, Environment environment) {
        String rawKey = generateKey(environment);
        ApiKey apiKey = ApiKey.builder()
                        .merchant(merchant)
                        .keyHash(CryptoUtil.sha256(rawKey))
                        .prefix(extractPrefix(rawKey))
                        .environment(environment)
                        .status(ApiKeyStatus.ACTIVE)
                        .build();
        apiKeyRepository.save(apiKey);
        return new GeneratedApiKeyResponse(rawKey, environment);
    }

    private String generateKey(Environment environment) {
        return "cyrus_" + environment.name().toLowerCase()
                + "_" + CryptoUtil.randomToken(32);
    }

    private String extractPrefix(String key) {
        return key.substring(0, 24);
    }
}