package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
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
 * Merchant lifecycle + dashboard config. Merchants hold no Nomba credentials (Cyrus uses its own
 * single account) and get a single API key — there is no TEST/LIVE split. The only outbound-facing
 * config a merchant owns is the single webhook endpoint Cyrus posts events to.
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
                walletService.getBalance(merchant.getId()));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyListItem> listApiKeys(String email) {
        return apiKeyService.listKeys(findByBusinessEmail(email).getId());
    }

    /** Generates the merchant's API key. The raw key is returned once. */
    public GeneratedApiKeysResponse createApiKey(String email) {
        return apiKeyService.createApiKey(findByBusinessEmail(email));
    }

    public void revokeApiKey(String email, UUID keyId) {
        apiKeyService.revokeKey(findByBusinessEmail(email).getId(), keyId);
    }

    /**
     * Registers or updates the outbound-webhook URL. On the first registration a signing secret is
     * generated and returned ONCE (never retrievable again); a later URL update keeps the existing
     * secret and returns null. Mutates the managed entity in place.
     */
    public WebhookConfigResponse setWebhookUrl(String email, String url) {
        WebhookUrlValidator.validate(url);

        Merchant merchant = findByBusinessEmail(email);
        WebhookConfig existing = merchant.getWebhookConfig();

        String plaintextSecret = null;
        String encryptedSecret;
        if (existing != null && existing.encryptedSecret() != null) {
            encryptedSecret = existing.encryptedSecret(); // keep the current secret on a URL change
        } else {
            plaintextSecret = generateWebhookSecret();
            encryptedSecret = CryptoUtil.encrypt(plaintextSecret, appProperties.encryptionKey());
        }

        merchant.setWebhookConfig(new WebhookConfig(url, encryptedSecret));
        log.info("Merchant {} set webhook URL", merchant.getId());
        return new WebhookConfigResponse(url, plaintextSecret, true);
    }

    /** Issues a fresh signing secret for an already-registered webhook, returned once. */
    public WebhookConfigResponse rotateWebhookSecret(String email) {
        Merchant merchant = findByBusinessEmail(email);
        WebhookConfig existing = merchant.getWebhookConfig();
        if (existing == null) {
            throw new EntityNotFoundException("No webhook configured");
        }
        String plaintextSecret = generateWebhookSecret();
        String encryptedSecret = CryptoUtil.encrypt(plaintextSecret, appProperties.encryptionKey());
        merchant.setWebhookConfig(new WebhookConfig(existing.url(), encryptedSecret));
        log.info("Merchant {} rotated webhook secret", merchant.getId());
        return new WebhookConfigResponse(existing.url(), plaintextSecret, true);
    }

    @Transactional(readOnly = true)
    public WebhookConfigItem getWebhook(String email) {
        WebhookConfig config = findByBusinessEmail(email).getWebhookConfig();
        if (config == null) {
            return null;
        }
        return new WebhookConfigItem(config.url(), config.encryptedSecret() != null);
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryItem> listWebhookDeliveries(String email, MerchantWebhookStatus status, Pageable pageable) {
        UUID merchantId = findByBusinessEmail(email).getId();
        return webhookEventRepository.findDeliveries(merchantId, status, pageable)
                .map(Mapper::toWebhookDeliveryItem);
    }

    public void deleteWebhook(String email) {
        Merchant merchant = findByBusinessEmail(email);
        merchant.setWebhookConfig(null);
        log.info("Merchant {} removed webhook config", merchant.getId());
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
