package com.ojo.cyrus.controllers.developer;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.enums.TransactionStatus;
import com.ojo.cyrus.enums.TransactionType;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.models.responses.TransactionResponse;
import com.ojo.cyrus.services.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = """
        Your transaction history — inbound customer payments and outbound payouts, across all customers.
        For CUSTOMER_PAYMENT transactions, feeKobo is Nomba's processing fee (1% min ₦10, max ₦150).
        For PAYOUT transactions, feeKobo is Cyrus's flat ₦30 fee.""")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "List transactions",
            description = "Paginated, newest-first history of every transaction on your account. Optionally " +
                    "narrow to one customer (`customerReference`), a transaction `type`, `status`, `matchStatus`, " +
                    "and/or a date range (`from`/`to`, ISO-8601 instants). For a single customer's statement " +
                    "(identity + reporting summary + this same list), see GET /v1/customers/{reference}/statement.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping
    public CyrusApiResponse<Page<TransactionResponse>> list(
            @AuthenticationPrincipal Merchant merchant,
            @RequestParam(required = false) String customerReference,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) MatchStatus matchStatus,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Transactions retrieved",
                transactionService.list(merchant.getId(), customerReference, type, status, matchStatus, from, to, pageable));
    }

    @Operation(
            summary = "Get a transaction",
            description = "Retrieves a single transaction by your reference.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping("/{reference}")
    public CyrusApiResponse<TransactionResponse> get(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Transaction retrieved",
                transactionService.getByReference(merchant.getId(), reference));
    }
}
