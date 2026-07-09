package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.CreatePayoutRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.PayoutResponse;
import com.ojo.cyrus.services.MerchantService;
import com.ojo.cyrus.services.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/me/payouts")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = """
        Withdraw your settled wallet balance to a registered bank beneficiary.
        Each payout incurs a flat ₦30 fee — ₦20 covers Nomba's fixed transfer charge,
        ₦10 is Cyrus's margin. The fee is deducted from your wallet alongside the
        transfer amount; total debit = amount + ₦30.""")
public class PayoutController {

    private final PayoutService payoutService;
    private final MerchantService merchantService;

    @Operation(summary = "Initiate a payout",
            description = """
                    Debits your wallet and transfers to a beneficiary via Nomba. The total wallet debit
                    = amount + ₦30 flat fee — the fee covers Nomba's ₦20 fixed transfer charge plus a
                    ₦10 Cyrus margin. Fails fast with 409 if your balance is insufficient; a provider
                    failure refunds the wallet (including the fee) automatically.""",
            security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping
    public CyrusApiResponse<PayoutResponse> initiate(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePayoutRequest request) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.CREATED, "Payout initiated",
                payoutService.initiate(merchantId, request));
    }

    @Operation(summary = "List payouts",
            description = "Paginated payout history, newest first.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<Page<PayoutResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payouts retrieved",
                payoutService.list(merchantId, pageable));
    }

    @Operation(summary = "Get a payout",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{id}")
    public CyrusApiResponse<PayoutResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Payout retrieved",
                payoutService.getForMerchant(merchantId, id));
    }
}
