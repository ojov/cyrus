package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.enums.MerchantStatus;
import com.ojo.cyrus.enums.TokenType;
import com.ojo.cyrus.exception.EntityNotFoundException;
import com.ojo.cyrus.models.dto.AuthTokenResult;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.entities.Token;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

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
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final WalletService walletService;

    public AuthTokenResult<MerchantRegistrationResponse> register(MerchantRegistrationRequest request) {
        merchantService.validateMerchantExists(request);
        Merchant merchantEntity = mapToMerchantEntity(request);
        merchantEntity.setPasswordHash(passwordEncoder.encode(request.password()));
        Merchant merchant = merchantService.save(merchantEntity);
        // Provision the merchant's wallet up front so payment credits always have a wallet to post against.
        walletService.provisionWallet(merchant);
        sendVerificationEmail(merchant);
        String jwt = tokenService.generateToken(merchant.getBusinessEmail(), "ROLE_MERCHANT");
        MerchantRegistrationResponse response = new MerchantRegistrationResponse(
                merchant.getId(), merchant.getBusinessName(), merchant.getBusinessEmail());
        return new AuthTokenResult<>(jwt, response);
    }

    public void verifyEmail(String tokenValue) {
        Token verificationToken = tokenService.validateToken(tokenValue, TokenType.EMAIL_VERIFICATION);
        Merchant merchant = verificationToken.getMerchant();
        merchant.setStatus(MerchantStatus.ACTIVE);
        verificationToken.setUsed(true);
        log.info("Merchant {} verified and activated", merchant.getBusinessEmail());
    }

    public AuthTokenResult<LoginResponse> login(LoginRequest request) {
        Authentication authentication = authenticationManager.
                authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        String jwt = tokenService.generateToken(authentication);
        Merchant merchant = merchantService.findByBusinessEmail(request.email());
        LoginResponse response = new LoginResponse(merchant.getId(), merchant.getBusinessName(), merchant.getBusinessEmail());
        return new AuthTokenResult<>(jwt, response);
    }

    public void resendVerificationEmail(String email) {
        Merchant merchant = merchantService.findByBusinessEmail(email);
        if (merchant.getStatus() == MerchantStatus.ACTIVE) {
            log.warn("Resend-verification requested for already-active merchant {}", email);
            return;
        }
        tokenService.invalidateOutstanding(merchant.getId(), TokenType.EMAIL_VERIFICATION);
        sendVerificationEmail(merchant);
    }

    public void forgotPassword(String email) {
        try {
            Merchant merchant = merchantService.findByBusinessEmail(email);
            tokenService.invalidateOutstanding(merchant.getId(), TokenType.PASSWORD_RESET);
            Token resetToken = tokenService.createToken(merchant, TokenType.PASSWORD_RESET, Duration.ofMinutes(15));
            String resetUrl = appProperties.frontendUrl() + "/reset-password?token=" + resetToken.getToken();
            emailService.sendPasswordResetEmail(merchant.getBusinessEmail(), merchant.getBusinessName(), resetUrl);
            log.info("Password reset email sent to {}", email);
        } catch (EntityNotFoundException e) {
            // Deliberately swallowed: the caller always sees the same generic "if an account exists"
            // response, whether or not the email matches a merchant — never reveal which.
            log.warn("Password reset requested for unknown email {}", email);
        }
    }

    public void resetPassword(String tokenValue, String newPassword) {
        Token resetToken = tokenService.validateToken(tokenValue, TokenType.PASSWORD_RESET);
        Merchant merchant = resetToken.getMerchant();
        merchant.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.setUsed(true);
        log.info("Password reset completed for merchant {}", merchant.getBusinessEmail());
    }

    private void sendVerificationEmail(Merchant merchant) {
        Token verificationToken = tokenService.createToken(merchant, TokenType.EMAIL_VERIFICATION, Duration.ofHours(24));
        String verificationUrl = appProperties.frontendUrl() + "/verify-email?token=" + verificationToken.getToken();
        emailService.sendVerificationEmail(merchant.getBusinessEmail(), merchant.getBusinessName(), verificationUrl);
        log.info("Verification email sent to {}", merchant.getBusinessEmail());
    }
}
