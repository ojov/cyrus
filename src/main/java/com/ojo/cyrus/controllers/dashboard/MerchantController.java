package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.MerchantWebhookStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.CreateApiKeyRequest;
import com.ojo.cyrus.models.requests.WebhookRegistrationRequest;
import com.ojo.cyrus.models.responses.ApiKeyListItem;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
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
            description = "Customer + virtual-account counts and TEST/LIVE wallet balances (kobo).",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/stats")
    public CyrusApiResponse<MerchantStatsResponse> stats(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Stats retrieved",
                merchantService.getStats(jwt.getSubject()));
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
            description = "Generates a new API key for the given environment (TEST or LIVE). The raw key is returned once.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/me/api-keys")
    public CyrusApiResponse<GeneratedApiKeysResponse> createApiKey(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApiKeyRequest request) {
        return CyrusApiResponse.success(ResponseCode.CREATED, "API key created",
                merchantService.createApiKey(jwt.getSubject(), request.environment()));
    }

    @Operation(summary = "Revoke an API key",
            security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/me/api-keys/{id}")
    public CyrusApiResponse<Void> revokeApiKey(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        merchantService.revokeApiKey(jwt.getSubject(), id);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "API key revoked", null);
    }

    @Operation(summary = "Register or update a webhook URL",
            description = "Sets the URL Cyrus POSTs payment.*/payout.* events to for the given environment. " +
                    "On first registration the signing secret is returned once — store it, it can't be retrieved again.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping("/me/webhooks/{environment}")
    public CyrusApiResponse<WebhookConfigResponse> setWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Environment environment,
            @Valid @RequestBody WebhookRegistrationRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook configured",
                merchantService.setWebhookUrl(jwt.getSubject(), environment, request.url()));
    }

    @Operation(summary = "Rotate a webhook signing secret",
            description = "Generates a new signing secret for the environment's webhook. The new secret is returned once.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping("/me/webhooks/{environment}/rotate-secret")
    public CyrusApiResponse<WebhookConfigResponse> rotateWebhookSecret(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Environment environment) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook secret rotated",
                merchantService.rotateWebhookSecret(jwt.getSubject(), environment));
    }

    @Operation(summary = "List webhook configurations",
            description = "Lists the merchant's configured webhook URLs per environment (secrets are never returned).",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/webhooks")
    public CyrusApiResponse<List<WebhookConfigItem>> listWebhooks(@AuthenticationPrincipal Jwt jwt) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhooks retrieved",
                merchantService.listWebhooks(jwt.getSubject()));
    }

    @Operation(summary = "Webhook delivery history",
            description = "Paginated history of outbound webhook deliveries, newest first, with optional status/environment filters.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me/webhooks/deliveries")
    public CyrusApiResponse<Page<WebhookDeliveryItem>> listWebhookDeliveries(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) MerchantWebhookStatus status,
            @RequestParam(required = false) Environment environment,
            @PageableDefault(size = 20) Pageable pageable) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook deliveries retrieved",
                merchantService.listWebhookDeliveries(jwt.getSubject(), status, environment, pageable));
    }

    @Operation(summary = "Remove a webhook configuration",
            security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/me/webhooks/{environment}")
    public CyrusApiResponse<Void> deleteWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Environment environment) {
        merchantService.deleteWebhook(jwt.getSubject(), environment);
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Webhook removed", null);
    }
}
