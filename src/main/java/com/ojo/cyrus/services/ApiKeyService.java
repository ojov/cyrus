package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.ApiKeyStatus;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.models.entities.ApiKey;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.repositories.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public GeneratedApiKey createApiKey(Merchant merchant, Environment environment) {
        String rawKey = generateKey(environment);
        ApiKey apiKey = ApiKey.builder()
                .merchant(merchant)
                .keyHash(
                        passwordEncoder.encode(rawKey)
                )
                .prefix(
                        extractPrefix(rawKey)
                )
                .environment(environment)
                .status(ApiKeyStatus.ACTIVE)
                .build();

        apiKeyRepository.save(apiKey);

        return new GeneratedApiKey(
                rawKey,
                environment
        );
    }


    public Merchant authenticate(String rawKey) {

        ApiKey apiKey = findMatchingKey(rawKey);

        if (apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            throw new InvalidApiKeyException();
        }

        Merchant merchant = apiKey.getMerchant();

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new MerchantNotActiveException();
        }

        apiKey.setLastUsedAt(LocalDateTime.now());

        return merchant;
    }


    public GeneratedApiKey rotate(
            Merchant merchant,
            NombaEnvironment environment
    ) {

        ApiKey oldKey =
                apiKeyRepository
                        .findByMerchantAndEnvironmentAndStatus(
                                merchant,
                                environment,
                                ApiKeyStatus.ACTIVE
                        )
                        .orElseThrow();


        oldKey.setStatus(ApiKeyStatus.REVOKED);


        return createApiKey(
                merchant,
                environment
        );
    }


    private ApiKey findMatchingKey(String rawKey) {

        String prefix = extractPrefix(rawKey);

        ApiKey apiKey =
                apiKeyRepository
                        .findByPrefix(prefix)
                        .orElseThrow(
                                InvalidApiKeyException::new
                        );


        if (!passwordEncoder.matches(
                rawKey,
                apiKey.getKeyHash()
        )) {
            throw new InvalidApiKeyException();
        }

        return apiKey;
    }


    private String generateKey(
            NombaEnvironment environment
    ) {

        byte[] bytes = new byte[32];

        new SecureRandom()
                .nextBytes(bytes);


        String random =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);


        return environment.name().toLowerCase()
                + "_cyrus_"
                + random;
    }


    private String extractPrefix(
            String key
    ) {

        // used for lookup only
        // cyrus_test_Abc123...
        return key.substring(0, 16);
    }


    public record GeneratedApiKey(
            String key,
            Environment environment
    ) {}
}