package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.config.properties.AuthCookieProperties;
import com.ojo.cyrus.models.dto.AuthTokenResult;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.ForgotPasswordRequest;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.requests.ResendVerificationRequest;
import com.ojo.cyrus.models.requests.ResetPasswordRequest;
import com.ojo.cyrus.models.requests.VerifyEmailRequest;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for merchant registration and login")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieProperties cookieProperties;
    private final AppProperties appProperties;

    @Operation(
            summary = "Register a new merchant",
            description = "Creates a new merchant account and sets an httpOnly session cookie for immediate " +
                    "dashboard access. The JWT is never returned in the response body.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Merchant registered successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data")
            }
    )
    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public CyrusApiResponse<MerchantRegistrationResponse> register(@Valid @RequestBody MerchantRegistrationRequest request,
                                                                    HttpServletResponse httpResponse) {
        AuthTokenResult<MerchantRegistrationResponse> result = authService.register(request);
        setAuthCookie(httpResponse, result.token());
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", result.response());
    }

    @Operation(
            summary = "Merchant login",
            description = "Authenticates a merchant with email and password and sets an httpOnly session cookie " +
                    "for dashboard access. The JWT is never returned in the response body.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            }
    )
    @PostMapping("/login")
    public CyrusApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse httpResponse) {
        AuthTokenResult<LoginResponse> result = authService.login(request);
        setAuthCookie(httpResponse, result.token());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Login successful", result.response());
    }

    @Operation(summary = "Log out", description = "Clears the dashboard session cookie.")
    @PostMapping("/logout")
    public CyrusApiResponse<Void> logout(HttpServletResponse httpResponse) {
        clearAuthCookie(httpResponse);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Logged out", null);
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
    @Operation(summary = "Request password reset", description = "Sends a password reset email with a 15-minute expiry.")
    @PostMapping("/forgot-password")
    public CyrusApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "If an account with that email exists, a reset link has been sent", null);
    }
    @Operation(summary = "Reset password", description = "Resets the password using a valid reset token.")
    @PostMapping("/reset-password")
    public CyrusApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Password reset successfully", null);
    }
    @Operation(summary = "Verify email", description = "Activates the merchant account using a valid email verification token.")
    @PostMapping("/verify-email")
    public CyrusApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Email verified successfully", null);
    }

    private void setAuthCookie(HttpServletResponse httpResponse, String jwt) {
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.name(), jwt)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(appProperties.jwt().expiryHours()))
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookie(HttpServletResponse httpResponse) {
        ResponseCookie cookie = ResponseCookie.from(cookieProperties.name(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
