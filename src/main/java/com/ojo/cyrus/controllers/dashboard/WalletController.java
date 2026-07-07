package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.WalletBalanceResponse;
import com.ojo.cyrus.services.MerchantService;
import com.ojo.cyrus.services.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/me/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Your settled balance held in Cyrus (kobo).")
public class WalletController {

    private final WalletService walletService;
    private final MerchantService merchantService;

    @Operation(summary = "Get wallet balance",
            description = "Your available balance in integer kobo.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<WalletBalanceResponse> getWallet(@AuthenticationPrincipal Jwt jwt) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Wallet balance retrieved",
                new WalletBalanceResponse(walletService.getBalance(merchantId)));
    }
}
