package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.exception.AlreadyExistsException;
import com.ojo.cyrus.exception.InvalidTokenException;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VerificationToken;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.GeneratedApiKeyResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.repositories.MerchantRepository;
import com.ojo.cyrus.repositories.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.ojo.cyrus.utils.Mapper.merchantToRegistrationResponse;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public MerchantRegistrationResponse register(MerchantRegistrationRequest request) {
        validate(request);

        Merchant merchant = merchantRepository.save(buildMerchant(request));
        GeneratedApiKeyResponse apiKey = apiKeyService.createApiKey(merchant, Environment.TEST);

        sendVerificationEmail(merchant);

        return merchantToRegistrationResponse(merchant, apiKey.apiKey());
    }

    public void verifyEmail(String tokenValue) {
        VerificationToken verificationToken = verificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification link"));

        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("This verification link has already been used");
        }
        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Verification link has expired. Please register again");
        }

        Merchant merchant = verificationToken.getMerchant();
        merchant.setStatus(MerchantStatus.ACTIVE);
        verificationToken.setUsed(true);

        log.info("Merchant {} verified and activated", merchant.getBusinessEmail());
    }

    private void sendVerificationEmail(Merchant merchant) {
        String tokenValue = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(tokenValue)
                .merchant(merchant)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        verificationTokenRepository.save(verificationToken);

        String verificationUrl = baseUrl + "/v1/auth/verify?token=" + tokenValue;
        emailService.sendVerificationEmail(merchant.getBusinessEmail(), merchant.getBusinessName(), verificationUrl);
        log.info("Verification email sent to {}", merchant.getBusinessEmail());
    }

    private void validate(MerchantRegistrationRequest request) {
        if (merchantRepository.existsByBusinessEmail(request.businessEmail())) {
            throw new AlreadyExistsException("An account with this email already exists");
        }
        if (merchantRepository.existsByNombaParentAccountId(request.nombaParentAccountId())) {
            throw new AlreadyExistsException("Nomba parent account already registered");
        }
    }

    private Merchant buildMerchant(MerchantRegistrationRequest request) {
        return Merchant.builder()
                .businessName(request.businessName())
                .businessEmail(request.businessEmail())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nombaParentAccountId(request.nombaParentAccountId())
                .nombaSubAccountIds(request.subAccountIds())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .build();
    }
}
