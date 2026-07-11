package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.PlatformOverviewResponse;
import com.ojo.cyrus.models.responses.PlatformProfitSummaryResponse;
import com.ojo.cyrus.services.PlatformAdminService;
import com.ojo.cyrus.services.PoolReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide oversight for Cyrus super-admins (JWT chain). Every endpoint first calls
 * {@link PlatformAdminService#requireSuperAdmin} — a non-admin merchant with a valid JWT gets a 403,
 * so this is safe to sit on the same default chain as the merchant dashboard.
 */
@RestController
@RequestMapping("/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Platform (super-admin)", description = "Platform-wide custody, totals, and integrity oversight.")
public class PlatformController {

    private final PlatformAdminService platformAdminService;
    private final PoolReconciliationService poolReconciliationService;

    @Operation(
            summary = "Platform overview",
            description = "Custody (total wallet liabilities vs live Nomba balance), platform totals, " +
                    "reconciliation health, orphaned/stuck items, and a ledger-integrity check. Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/overview")
    public CyrusApiResponse<PlatformOverviewResponse> overview(@AuthenticationPrincipal Jwt jwt) {
        platformAdminService.requireSuperAdmin(jwt.getSubject());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Platform overview retrieved",
                platformAdminService.getOverview());
    }

    @Operation(
            summary = "Platform profit summary",
            description = "Expected vs actual provider balance, total inflows/outflows, accrued fees, " +
                    "merchant liabilities, and reconciliation status. Super-admin only.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/profit")
    public CyrusApiResponse<PlatformProfitSummaryResponse> profitSummary(@AuthenticationPrincipal Jwt jwt) {
        platformAdminService.requireSuperAdmin(jwt.getSubject());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Profit summary retrieved",
                poolReconciliationService.getSummary());
    }
}
