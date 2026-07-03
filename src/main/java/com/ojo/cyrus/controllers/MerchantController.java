package com.ojo.cyrus.controllers;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.GoLiveRequest;
import com.ojo.cyrus.models.requests.UpdateSubAccountsRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.GeneratedApiKeysResponse;
import com.ojo.cyrus.models.responses.SubAccountBalanceResponse;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@Tag(name = "Merchant Operations", description = "Dashboard operations for authenticated merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(
            summary = "Get sub-account balances",
            description = "Returns the balance of the parent account and all configured sub-accounts from Nomba.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @GetMapping("/me/subaccounts/balances")
    public CyrusApiResponse<List<SubAccountBalanceResponse>> getSubAccountBalances(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "TEST") Environment environment) {

        String email = jwt.getSubject();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Balances retrieved",
                merchantService.getSubAccountBalances(email, environment));
    }

    @Operation(
            summary = "Update sub-accounts",
            description = "Replaces the merchant's configured Nomba sub-account IDs. Use this to add new sub-accounts from the dashboard.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @PatchMapping("/me/subaccounts")
    public CyrusApiResponse<Void> updateSubAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSubAccountsRequest request) {

        String email = jwt.getSubject();
        merchantService.updateSubAccounts(email, request.subAccountIds());
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Sub-accounts updated", null);
    }

    @Operation(
            summary = "Activate live mode",
            description = "Submit your LIVE Nomba client id and secret. Cyrus verifies them with Nomba, " +
                    "then issues your live API key. Parent/sub-account ids are shared with test.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @PostMapping("/me/go-live")
    public CyrusApiResponse<GeneratedApiKeysResponse> goLive(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoLiveRequest request) {

        String email = jwt.getSubject();
        return CyrusApiResponse.success(ResponseCode.CREATED, "Live mode activated",
                merchantService.goLive(email, request));
    }
}
