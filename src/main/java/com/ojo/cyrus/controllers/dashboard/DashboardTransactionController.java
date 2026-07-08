package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.TransactionResponse;
import com.ojo.cyrus.services.MerchantService;
import com.ojo.cyrus.services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JWT-authed mirror of {@link com.ojo.cyrus.controllers.developer.TransactionController} for the ops
 * dashboard — same reasoning as {@link DashboardCustomerController}: the developer-facing
 * {@code /v1/transactions/**} endpoints sit on the API-key chain, so the JWT-cookie dashboard needs
 * its own path onto the same {@link TransactionService} logic.
 */
@RestController
@RequestMapping("/v1/merchants/me/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions (dashboard)", description = "Read-only transaction history for the ops dashboard.")
public class DashboardTransactionController {

    private final TransactionService transactionService;
    private final MerchantService merchantService;

    @Operation(summary = "List transactions", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    public CyrusApiResponse<Page<TransactionResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String customerReference,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) MatchStatus matchStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Transactions retrieved",
                transactionService.list(merchantId, customerReference, type, status, matchStatus, from, to, pageable));
    }

    @Operation(summary = "Get a transaction", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{reference}")
    public CyrusApiResponse<TransactionResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reference) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Transaction retrieved",
                transactionService.getByReference(merchantId, reference));
    }
}
