package com.ojo.cyrus.services;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.VerificationToken;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.ojo.cyrus.utils.Mapper.mapToMerchantEntity;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final MerchantService merchantService;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyService apiKeyService;
    private final EmailService emailService;
    @Value("${app.base-url}")
    private String baseUrl;

    public MerchantRegistrationResponse register(MerchantRegistrationRequest request) {
        merchantService.validateMerchantExists(request);
        String encodedPassword = passwordEncoder.encode(request.password());
        Merchant merchantEntity = mapToMerchantEntity(request);
        merchantEntity.setPasswordHash(encodedPassword);
        Merchant merchant = merchantService.save(merchantEntity);
        GeneratedApiKeysResponse apiKeys = apiKeyService.createApiKey(merchant, Environment.TEST);
        sendVerificationEmail(merchant);
        String jwt = tokenService.generateToken(merchant.getBusinessEmail(), "ROLE_MERCHANT");
        return new MerchantRegistrationResponse(merchant.getId(), merchant.getBusinessName(),
                merchant.getBusinessEmail(), jwt, apiKeys);
    }

    public void verifyEmail(String tokenValue) {
       VerificationToken verificationToken = tokenService.validateVerificationToken(tokenValue);
        Merchant merchant = verificationToken.getMerchant();
        merchant.setStatus(MerchantStatus.ACTIVE);
        verificationToken.setUsed(true);
        log.info("Merchant {} verified and activated", merchant.getBusinessEmail());
    }


    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.
                authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        String jwt = tokenService.generateToken(authentication);
        Merchant merchant = merchantService.findByBusinessEmail(request.email());
       return new LoginResponse(jwt, "Bearer", merchant.getId(), merchant.getBusinessName(), merchant.getBusinessEmail());
    }

    private void sendVerificationEmail(Merchant merchant) {
        String tokenValue = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .token(tokenValue)
                .merchant(merchant)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        tokenService.saveVerificationToken(verificationToken);

        String verificationUrl = baseUrl + "/v1/auth/verify?token=" + tokenValue;
        emailService.sendVerificationEmail(merchant.getBusinessEmail(), merchant.getBusinessName(), verificationUrl);
        log.info("Verification email sent to {}", merchant.getBusinessEmail());
    }
}
