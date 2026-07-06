package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.EmailNotVerifiedException;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.exception.NombaVerificationException;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.models.NombaCredential;
import com.ojo.cyrus.models.WebhookConfig;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.GoLiveRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.ApiKeyListItem;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.MerchantStatsResponse;
import com.ojo.cyrus.models.responses.SubAccountBalanceResponse;
import com.ojo.cyrus.models.responses.WebhookConfigItem;
import com.ojo.cyrus.models.responses.WebhookConfigResponse;
import com.ojo.cyrus.models.responses.WebhookDeliveryItem;
import com.ojo.cyrus.nomba.*;
import com.ojo.cyrus.nomba.dto.NombaBalanceData;
import com.ojo.cyrus.nomba.dto.NombaCredentials;
import com.ojo.cyrus.nomba.service.NombaAuthenticationService;
import com.ojo.cyrus.nomba.utils.CredentialMapper;
import com.ojo.cyrus.nomba.utils.NombaCurrencyUtil;
import com.ojo.cyrus.repositories.CustomerRepository;
import com.ojo.cyrus.repositories.MerchantRepository;
import com.ojo.cyrus.repositories.MerchantWebhookEventRepository;
import com.ojo.cyrus.repositories.VirtualAccountRepository;
import com.ojo.cyrus.utils.CryptoUtil;
import com.ojo.cyrus.utils.Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final NombaClient nombaClient;
    private final CredentialMapper credentialMapper;
    private final NombaAuthenticationService nombaAuthService;
    private final ApiKeyService apiKeyService;
    private final AppProperties appProperties;
    private final CustomerRepository customerRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final MerchantWebhookEventRepository webhookEventRepository;
    private final PlatformTransactionManager transactionManager;

    private static final String WEBHOOK_SECRET_PREFIX = "whsec_";

    @Transactional(readOnly = true)
    public Merchant findById(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    /**
     * Resolves a merchant's Nomba credentials into a detached, fully-materialized value so
     * callers can perform the external Nomba call without holding a DB transaction open.
     */
    @Transactional(readOnly = true)
    public NombaCredentials getNombaCredentials(UUID merchantId) {
        return credentialMapper.fromMerchant(findById(merchantId));
    }

    public Merchant findByBusinessEmail(String email) {
        return merchantRepository.findByBusinessEmail(email).orElseThrow(() -> new EntityNotFoundException("Merchant not found"));
    }

    public Merchant save(Merchant merchant) {
        return merchantRepository.save(merchant);
    }

    // NOT_SUPPORTED opts this method out of the class-level @Transactional so the external
    // Nomba calls do not run inside a DB transaction. Credentials (incl. parent + sub-account
    // ids) are materialized in a short read-only transaction first, then the connection is
    // released before the HTTP calls.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SubAccountBalanceResponse> getSubAccountBalances(String email, Environment env) {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);
        NombaCredentials creds = readOnlyTx.execute(status ->
                credentialMapper.fromMerchant(findByBusinessEmail(email)));

        List<SubAccountBalanceResponse> balances = new ArrayList<>();

        NombaBalanceData parentBalance = nombaClient.getParentAccountBalance(creds, env);
        balances.add(new SubAccountBalanceResponse(creds.parentAccountId(), "PARENT",
                NombaCurrencyUtil.nairaToKobo(parentBalance.amount()), parentBalance.currency(), Instant.now()));

        for (String subAccountId : creds.subAccountIds()) {
            NombaBalanceData data = nombaClient.getSubAccountBalance(creds, subAccountId, env);
            balances.add(new SubAccountBalanceResponse(
                    subAccountId, "SUB",
                    NombaCurrencyUtil.nairaToKobo(data.amount()), data.currency(), Instant.now()));
        }

        return balances;
    }

    /**
     * Activates live mode for a merchant. In Nomba, parent/sub-account ids are static across
     * environments — only the client id/secret change — so we only collect live credentials.
     * We verify them against Nomba (outside any DB transaction, per the provider-call convention)
     * before storing them encrypted and issuing the live API key.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GeneratedApiKeysResponse goLive(String email, GoLiveRequest request) {
        // Only verified merchants can go live.
        TransactionTemplate checkTx = new TransactionTemplate(transactionManager);
        checkTx.setReadOnly(true);
        checkTx.executeWithoutResult(status -> {
            Merchant merchant = findByBusinessEmail(email);
            if (merchant.getStatus() != MerchantStatus.ACTIVE) {
                throw new EmailNotVerifiedException("Please verify your email before activating live mode.");
            }
        });

        String encryptedSecret = CryptoUtil.encrypt(request.nombaClientSecret(), appProperties.encryptionKey());
        NombaCredential liveCredential = new NombaCredential(request.nombaClientId(), encryptedSecret);

        // Materialize what the verification call needs in a short read-only transaction.
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);
        NombaCredentials verifyCreds = readTx.execute(status -> {
            Merchant merchant = findByBusinessEmail(email);
            return new NombaCredentials(
                    merchant.getId().toString(),
                    merchant.getNombaParentAccountId(),
                    Map.of(Environment.LIVE, liveCredential),
                    new HashSet<>(merchant.getNombaSubAccountIds()));
        });

        // Verify with Nomba OUTSIDE any DB transaction — reject go-live if the credentials are bad.
        try {
            nombaAuthService.getAccessToken(verifyCreds, Environment.LIVE);
        } catch (RuntimeException e) {
            log.warn("Live Nomba credential verification failed for {}: {}", email, e.getMessage());
            throw new NombaVerificationException(
                    "Could not verify your live Nomba credentials. Please check your client id and secret.");
        }

        // Persist the live credentials and issue the live API key in a short write transaction.
        GeneratedApiKeysResponse liveKeys = new TransactionTemplate(transactionManager).execute(status -> {
            Merchant merchant = findByBusinessEmail(email);
            merchant.getNombaCredentials().put(Environment.LIVE, liveCredential);
            return apiKeyService.createApiKey(merchant, Environment.LIVE);
        });

        log.info("Merchant {} activated live mode", email);
        return liveKeys;
    }

    @Transactional(readOnly = true)
    public MerchantStatsResponse getStats(String email) {
        Merchant merchant = findByBusinessEmail(email);
        boolean liveModeActive = merchant.getNombaCredentials().containsKey(Environment.LIVE);
        return new MerchantStatsResponse(
                customerRepository.countByMerchantId(merchant.getId()),
                virtualAccountRepository.countByMerchantId(merchant.getId()),
                liveModeActive);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyListItem> listApiKeys(String email) {
        return apiKeyService.listKeys(findByBusinessEmail(email).getId());
    }

    public GeneratedApiKeysResponse createApiKey(String email, Environment environment) {
        Merchant merchant = findByBusinessEmail(email);
        if (environment == Environment.LIVE && !merchant.getNombaCredentials().containsKey(Environment.LIVE)) {
            throw new NombaVerificationException("Activate live mode in settings before creating a live API key.");
        }
        return apiKeyService.createApiKey(merchant, environment);
    }

    public void revokeApiKey(String email, UUID keyId) {
        apiKeyService.revokeKey(findByBusinessEmail(email).getId(), keyId);
    }

    public void updateSubAccounts(String email, Set<String> subAccountIds) {
        Merchant merchant = findByBusinessEmail(email);
        merchant.setNombaSubAccountIds(subAccountIds);
    }

    /**
     * Registers or updates the outbound-webhook URL for one environment. On the first registration
     * a signing secret is generated and returned ONCE (never retrievable again); a later URL update
     * keeps the existing secret and returns it as null. Mutates the managed entity in place (dirty
     * checking persists on commit), mirroring {@link #updateSubAccounts}.
     */
    public WebhookConfigResponse setWebhookUrl(String email, Environment environment, String url) {
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
        if (merchantRepository.existsByNombaParentAccountId(request.nombaParentAccountId())) {
            throw new AlreadyExistsException("Nomba parent account already registered");
        }
    }

}
