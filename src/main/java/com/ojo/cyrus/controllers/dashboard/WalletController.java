package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.Environment;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/merchants/me/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallets", description = "Your settled balances held in Cyrus, per environment (kobo).")
public class WalletController {

    private final WalletService walletService;
    private final MerchantService merchantService;

    @Operation(summary = "Get wallet balances",
            description = "Available balance for each environment (TEST and LIVE), in integer kobo.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<List<WalletBalanceResponse>> getWallets(@AuthenticationPrincipal Jwt jwt) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        List<WalletBalanceResponse> balances = Arrays.stream(Environment.values())
                .map(env -> new WalletBalanceResponse(env, walletService.getBalance(merchantId, env)))
                .toList();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Wallet balances retrieved", balances);
    }
}
