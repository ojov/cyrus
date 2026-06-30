package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.ApiKeyStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.entities.ApiKey;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.ApiKeyResponse;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.repositories.ApiKeyRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public Optional<ApiKey> validateAndGetKey(String rawKey) {
        String hash = CryptoUtil.sha256(rawKey);
        return apiKeyRepository.findByKeyHash(hash)
                .filter(key -> key.getStatus() == ApiKeyStatus.ACTIVE)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(Instant.now()));
    }

    public boolean isLiveKey(String rawKey) {
        return rawKey.contains("_live_");
    }

    public GeneratedApiKeysResponse createApiKey(Merchant merchant, Environment environment) {
        String rawKey = generateKey(environment);
        ApiKey apiKey = ApiKey.builder()
                        .merchant(merchant)
                        .keyHash(CryptoUtil.sha256(rawKey))
                        .prefix(extractPrefix(rawKey))
                        .environment(environment)
                        .status(ApiKeyStatus.ACTIVE)
                        .build();
        apiKeyRepository.save(apiKey);
        return new GeneratedApiKeysResponse(Set.of(new ApiKeyResponse(rawKey, environment, Instant.now())));
    }

    private String generateKey(Environment environment) {
        return "cyrus_" + environment.name().toLowerCase()
                + "_" + CryptoUtil.randomToken(32);
    }

    private String extractPrefix(String key) {
        return key.substring(0, 24);
    }
}