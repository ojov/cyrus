package com.ojo.cyrus.services;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.exception.InvalidTokenException;
import com.ojo.cyrus.models.entities.VerificationToken;
import com.ojo.cyrus.repositories.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final AppProperties appProperties;

    public String generateToken(Authentication authentication) {
        return generateToken(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" ")));
    }

    public String generateToken(String subject, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(appProperties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plus(appProperties.jwt().expiryHours(), ChronoUnit.HOURS))
                .subject(subject)
                .claim("scope", scope)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public VerificationToken validateVerificationToken(String tokenValue){
        VerificationToken verificationToken = getVerificationByTokenValue(tokenValue);
        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("This verification link has already been used");
        }
        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Verification link has expired. Please register again");
        }
        return verificationToken;
    }

    public VerificationToken getVerificationByTokenValue(String tokenValue){
        return verificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification link"));
    }

    public VerificationToken saveVerificationToken(VerificationToken verificationToken){
        return verificationTokenRepository.save(verificationToken);
    }

    public void deleteVerificationToken(UUID tokenValue){
        verificationTokenRepository.deleteById(tokenValue);
    }
}
