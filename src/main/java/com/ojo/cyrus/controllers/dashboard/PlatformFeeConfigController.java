package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.UpdateFeeConfigRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.FeeConfigResponse;
import com.ojo.cyrus.services.FeeConfigService;
import com.ojo.cyrus.services.PlatformAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Platform-wide fee configuration for Cyrus super-admins (JWT chain). All endpoints first call
 * {@link PlatformAdminService#requireSuperAdmin} — a non-admin merchant with a valid JWT gets a 403.
 */
@RestController
@RequestMapping("/v1/platform/fees")
@RequiredArgsConstructor
@Tag(name = "Platform Fees (super-admin)", description = "Manage the platform-wide fee configuration.")
public class PlatformFeeConfigController {

    private final PlatformAdminService platformAdminService;
    private final FeeConfigService feeConfigService;

    @Operation(
            summary = "Get fee configuration",
            description = "Returns the current platform fee configuration (inflow percent, min/max, payout flat fee). Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<FeeConfigResponse> get(@AuthenticationPrincipal Jwt jwt) {
        platformAdminService.requireSuperAdmin(jwt.getSubject());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Fee configuration retrieved",
                feeConfigService.get());
    }

    @Operation(
            summary = "Update fee configuration",
            description = "Updates the platform fee configuration. All fields are required. " +
                    "Takes effect immediately for all subsequent transactions. Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping
    public CyrusApiResponse<FeeConfigResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateFeeConfigRequest request) {
        platformAdminService.requireSuperAdmin(jwt.getSubject());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Fee configuration updated",
                feeConfigService.update(request));
    }
}
