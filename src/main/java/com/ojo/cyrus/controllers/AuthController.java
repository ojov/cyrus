package com.ojo.cyrus.controllers;

import com.ojo.cyrus.common.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.repositories.MerchantRepository;
import com.ojo.cyrus.services.TokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login with business email and password")
public class AuthController {

    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final MerchantRepository merchantRepository;

    @PostMapping("/login")
    public CyrusApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        String jwt = tokenService.generateToken(authentication);
        Merchant merchant = merchantRepository.findByBusinessEmail(request.email()).orElseThrow();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Login successful",
                new LoginResponse(jwt, "Bearer", merchant.getId(), merchant.getBusinessName(), merchant.getBusinessEmail()));
    }
}
