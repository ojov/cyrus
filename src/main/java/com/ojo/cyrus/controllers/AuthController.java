package com.ojo.cyrus.controllers;

import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.requests.ResendVerificationRequest;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for merchant registration and login")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a new merchant",
            description = "Creates a new merchant account and returns initial API keys and a JWT for immediate dashboard access.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Merchant registered successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data")
            }
    )
    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public CyrusApiResponse<MerchantRegistrationResponse> register(@Valid @RequestBody MerchantRegistrationRequest request){
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", authService.register(request));
    }
    @Operation(
            summary = "Merchant login",
            description = "Authenticates a merchant with email and password and returns a JWT for dashboard access.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            }
    )
    @PostMapping("/login")
    public CyrusApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Login successful", authService.login(request));
    }
    @Operation(
            summary = "Resend verification email",
            description = "Resends the email verification link to the merchant's email address. Invalidates any existing unused tokens."
    )
    @PostMapping("/resend-verification")
    public CyrusApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Verification email sent successfully", null);
    }
}
