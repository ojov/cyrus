package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.WebhookConfig;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.ApiKeyListItem;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.MerchantStatsResponse;
import com.ojo.cyrus.models.responses.WebhookConfigItem;
import com.ojo.cyrus.models.responses.WebhookConfigResponse;
import com.ojo.cyrus.models.responses.WebhookDeliveryItem;
import com.ojo.cyrus.repositories.MerchantCustomerRepository;
import com.ojo.cyrus.repositories.MerchantRepository;
import com.ojo.cyrus.repositories.MerchantWebhookEventRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import com.ojo.cyrus.utils.Mapper;
import com.ojo.cyrus.utils.WebhookUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Merchant lifecycle + dashboard config. Merchants no longer hold Nomba credentials (Cyrus uses its
 * own single account), so there is no "go live" credential flow or sub-account management — a LIVE
 * API key can be minted at any time. The only per-environment config a merchant owns is the outbound
 * webhook endpoint Cyrus posts events to.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ApiKeyService apiKeyService;
    private final AppProperties appProperties;
    private final MerchantCustomerRepository merchantCustomerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final MerchantWebhookEventRepository webhookEventRepository;
    private final WalletService walletService;

    private static final String WEBHOOK_SECRET_PREFIX = "whsec_";

    @Transactional(readOnly = true)
    public Merchant findById(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    public Merchant findByBusinessEmail(String email) {
        return merchantRepository.findByBusinessEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    public Merchant save(Merchant merchant) {
        return merchantRepository.save(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantStatsResponse getStats(String email) {
        Merchant merchant = findByBusinessEmail(email);
        return new MerchantStatsResponse(
                merchantCustomerRepository.countByMerchantId(merchant.getId()),
                virtualAccountRepository.countByMerchantCustomerMerchantId(merchant.getId()),
                walletService.getBalance(merchant.getId(), Environment.TEST),
                walletService.getBalance(merchant.getId(), Environment.LIVE));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyListItem> listApiKeys(String email) {
        return apiKeyService.listKeys(findByBusinessEmail(email).getId());
    }

    /** A merchant may create a key for either environment at any time (creds are platform-level now). */
    public GeneratedApiKeysResponse createApiKey(String email, Environment environment) {
        return apiKeyService.createApiKey(findByBusinessEmail(email), environment);
    }

    public void revokeApiKey(String email, UUID keyId) {
        apiKeyService.revokeKey(findByBusinessEmail(email).getId(), keyId);
    }

    /**
     * Registers or updates the outbound-webhook URL for one environment. On the first registration a
     * signing secret is generated and returned ONCE (never retrievable again); a later URL update
     * keeps the existing secret and returns null. Mutates the managed entity in place.
     */
    public WebhookConfigResponse setWebhookUrl(String email, Environment environment, String url) {
        WebhookUrlValidator.validate(url);

        Merchant merchant = findByBusinessEmail(email);
        WebhookConfig existing = merchant.getWebhookConfigs().get(environment);

        String plaintextSecret = null;
        String encryptedSecret;
        if (existing != null && existing.encryptedSecret() != null) {
            encryptedSecret = existing.encryptedSecret(); // keep the current secret on a URL change
        } else {
            plaintextSecret = generateWebhookSecret();
            encryptedSecret = CryptoUtil.encrypt(plaintextSecret, appProperties.encryptionKey());
        }

        merchant.getWebhookConfigs().put(environment, new WebhookConfig(url, encryptedSecret));
        log.info("Merchant {} set {} webhook URL", merchant.getId(), environment);
        return new WebhookConfigResponse(environment, url, plaintextSecret, true);
    }

    /** Issues a fresh signing secret for an already-registered webhook, returned once. */
    public WebhookConfigResponse rotateWebhookSecret(String email, Environment environment) {
        Merchant merchant = findByBusinessEmail(email);
        WebhookConfig existing = merchant.getWebhookConfigs().get(environment);
        if (existing == null) {
            throw new EntityNotFoundException("No webhook configured for " + environment);
        }
        String plaintextSecret = generateWebhookSecret();
        String encryptedSecret = CryptoUtil.encrypt(plaintextSecret, appProperties.encryptionKey());
        merchant.getWebhookConfigs().put(environment, new WebhookConfig(existing.url(), encryptedSecret));
        log.info("Merchant {} rotated {} webhook secret", merchant.getId(), environment);
        return new WebhookConfigResponse(environment, existing.url(), plaintextSecret, true);
    }

    @Transactional(readOnly = true)
    public List<WebhookConfigItem> listWebhooks(String email) {
        Merchant merchant = findByBusinessEmail(email);
        return merchant.getWebhookConfigs().entrySet().stream()
                .map(e -> new WebhookConfigItem(e.getKey(), e.getValue().url(),
                        e.getValue().encryptedSecret() != null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryItem> listWebhookDeliveries(String email, MerchantWebhookStatus status,
                                                           Environment environment, Pageable pageable) {
        UUID merchantId = findByBusinessEmail(email).getId();
        return webhookEventRepository.findDeliveries(merchantId, status, environment, pageable)
                .map(Mapper::toWebhookDeliveryItem);
    }

    public void deleteWebhook(String email, Environment environment) {
        Merchant merchant = findByBusinessEmail(email);
        merchant.getWebhookConfigs().remove(environment);
        log.info("Merchant {} removed {} webhook config", merchant.getId(), environment);
    }

    private String generateWebhookSecret() {
        return WEBHOOK_SECRET_PREFIX + CryptoUtil.randomToken(32);
    }

    public void validateMerchantExists(MerchantRegistrationRequest request) {
        if (merchantRepository.existsByBusinessEmail(request.businessEmail())) {
            throw new AlreadyExistsException("An account with this email already exists");
        }
    }
}
