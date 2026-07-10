package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.UpdateMerchantProfileRequest;
import com.ojo.cyrus.models.requests.WebhookRegistrationRequest;
import com.ojo.cyrus.models.responses.ApiKeyListItem;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.MerchantProfileResponse;
import com.ojo.cyrus.models.responses.MerchantStatsResponse;
import com.ojo.cyrus.models.responses.WebhookConfigItem;
import com.ojo.cyrus.models.responses.WebhookConfigResponse;
import com.ojo.cyrus.models.responses.WebhookDeliveryItem;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@Tag(name = "Merchant Operations", description = "Dashboard operations for authenticated merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "Dashboard stats",
            description = "Customer + virtual-account counts and wallet balance (kobo).",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/stats")
    public CyrusApiResponse<MerchantStatsResponse> stats(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Stats retrieved",
                merchantService.getStats(jwt.getSubject()));
    }

    @Operation(summary = "Get your profile",
            description = "Returns the authenticated merchant's current profile (business name, email, type, phone, BVN). " +
                    "Email is the login identifier and cannot be changed via this endpoint.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/profile")
    public CyrusApiResponse<MerchantProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Profile retrieved",
                merchantService.getProfile(jwt.getSubject()));
    }

    @Operation(summary = "Update your profile",
            description = "Partial update of the authenticated merchant's profile. Only non-null fields in the request body are changed. " +
                    "Business email and password cannot be changed here — email is the login identifier (changing it requires " +
                    "a verification flow) and password has its own reset endpoint.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PatchMapping("/me/profile")
    public CyrusApiResponse<MerchantProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMerchantProfileRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Profile updated",
                merchantService.updateProfile(jwt.getSubject(), request));
    }

    @Operation(summary = "List API keys",
            description = "Lists the merchant's API keys (metadata only — the raw key is shown once, at creation).",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/api-keys")
    public CyrusApiResponse<List<ApiKeyListItem>> listApiKeys(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "API keys retrieved",
                merchantService.listApiKeys(jwt.getSubject()));
    }

    @Operation(summary = "Create an API key",
            description = "Generates a new API key. The raw key is returned once.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/me/api-keys")
    public CyrusApiResponse<GeneratedApiKeysResponse> createApiKey(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.CREATED, "API key created",
                merchantService.createApiKey(jwt.getSubject()));
    }

    @Operation(summary = "Revoke an API key",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/me/api-keys/{id}/revoke")
    public CyrusApiResponse<Void> revokeApiKey(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        merchantService.revokeApiKey(jwt.getSubject(), id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "API key revoked", null);
    }

    @Operation(summary = "Delete a revoked API key",
            description = "Permanently removes a revoked API key. Only keys with REVOKED status can be deleted.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/me/api-keys/{id}")
    public CyrusApiResponse<Void> deleteApiKey(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        merchantService.deleteApiKey(jwt.getSubject(), id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "API key deleted", null);
    }

    @Operation(summary = "Register or update the webhook URL",
            description = "Sets the URL Cyrus POSTs payment.*/payout.* events to. On first registration the " +
                    "signing secret is returned once — store it, it can't be retrieved again.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping("/me/webhooks")
    public CyrusApiResponse<WebhookConfigResponse> setWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WebhookRegistrationRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook configured",
                merchantService.setWebhookUrl(jwt.getSubject(), request.url()));
    }

    @Operation(summary = "Rotate the webhook signing secret",
            description = "Generates a new signing secret. The new secret is returned once.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/me/webhooks/rotate-secret")
    public CyrusApiResponse<WebhookConfigResponse> rotateWebhookSecret(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook secret rotated",
                merchantService.rotateWebhookSecret(jwt.getSubject()));
    }

    @Operation(summary = "Get the webhook configuration",
            description = "The merchant's configured webhook URL (the secret is never returned).",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/webhooks")
    public CyrusApiResponse<WebhookConfigItem> getWebhook(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook retrieved",
                merchantService.getWebhook(jwt.getSubject()));
    }

    @Operation(summary = "Webhook delivery history",
            description = "Paginated history of outbound webhook deliveries, newest first, with an optional status filter.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/webhooks/deliveries")
    public CyrusApiResponse<Page<WebhookDeliveryItem>> listWebhookDeliveries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) MerchantWebhookStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook deliveries retrieved",
                merchantService.listWebhookDeliveries(jwt.getSubject(), status, pageable));
    }

    @Operation(summary = "Remove the webhook configuration",
            security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/me/webhooks")
    public CyrusApiResponse<Void> deleteWebhook(@AuthenticationPrincipal Jwt jwt) {
        merchantService.deleteWebhook(jwt.getSubject());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook removed", null);
    }
}
