package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.config.properties.AppProperties;
import com.ojo.cyrus.config.properties.AuthCookieProperties;
import com.ojo.cyrus.exception.InvalidTokenException;
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
import com.ojo.cyrus.services.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
    private final TokenService tokenService;
    private final AuthCookieProperties cookieProperties;
    private final AppProperties appProperties;

    @Operation(
            summary = "Register a new merchant",
            description = "Creates a new merchant account and sets httpOnly session cookies for immediate " +
                    "dashboard access. The JWT is never returned in the response body.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Merchant registered successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data")
            }
    )
    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public CyrusApiResponse<MerchantRegistrationResponse> register(@Valid @RequestBody MerchantRegistrationRequest request,
                                                                    HttpServletRequest httpRequest,
                                                                    HttpServletResponse httpResponse) {
        AuthTokenResult<MerchantRegistrationResponse> result = authService.register(
                request, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
        setAuthCookies(httpResponse, result.tokenPair());
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", result.response());
    }

    @Operation(
            summary = "Merchant login",
            description = "Authenticates a merchant with email and password and sets httpOnly session cookies " +
                    "for dashboard access. The JWT is never returned in the response body.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            }
    )
    @PostMapping("/login")
    public CyrusApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        AuthTokenResult<LoginResponse> result = authService.login(
                request, httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
        setAuthCookies(httpResponse, result.tokenPair());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Login successful", result.response());
    }

    @Operation(
            summary = "Refresh session",
            description = "Issues a new access token and refresh token pair using a valid refresh token. " +
                    "The old refresh token is revoked (rotation). The new tokens are set as httpOnly cookies."
    )
    @PostMapping("/refresh")
    public CyrusApiResponse<Void> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshTokenValue = extractCookie(httpRequest, cookieProperties.refreshName());
        if (refreshTokenValue == null) {
            throw new InvalidTokenException("No refresh token provided");
        }

        String userAgent = httpRequest.getHeader("User-Agent");
        String ipAddress = httpRequest.getRemoteAddr();
        TokenService.TokenPair tokenPair = tokenService.refreshAccessToken(refreshTokenValue, userAgent, ipAddress);

        setAuthCookies(httpResponse, tokenPair);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Token refreshed", null);
    }

    @Operation(summary = "Log out", description = "Clears the dashboard session cookies and revokes the refresh token.")
    @PostMapping("/logout")
    public CyrusApiResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshTokenValue = extractCookie(httpRequest, cookieProperties.refreshName());
        if (refreshTokenValue != null) {
            tokenService.revokeRefreshToken(refreshTokenValue);
        }
        clearAuthCookies(httpResponse);
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

    private static final Duration ACCESS_TOKEN_COOKIE_MAX_AGE = Duration.ofMinutes(15);

    private void setAuthCookies(HttpServletResponse httpResponse, TokenService.TokenPair tokenPair) {
        ResponseCookie accessCookie = ResponseCookie.from(cookieProperties.name(), tokenPair.accessToken())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(ACCESS_TOKEN_COOKIE_MAX_AGE)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(cookieProperties.refreshName(), tokenPair.refreshToken())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(appProperties.jwt().refreshExpiryDays()))
                .build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse httpResponse) {
        ResponseCookie accessCookie = ResponseCookie.from(cookieProperties.name(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from(cookieProperties.refreshName(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieName)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
