package com.ojo.cyrus.controllers.dashboard;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.CustomerService;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JWT-authed mirror of {@link com.ojo.cyrus.controllers.developer.CustomerController} for the ops
 * dashboard — the developer-facing {@code /v1/customers/**} endpoints sit on the API-key chain
 * (meant for a merchant's own backend integration), so the dashboard UI (JWT cookie session, no
 * API key) needs its own path onto the same {@link CustomerService} logic.
 */
@RestController
@RequestMapping("/v1/merchants/me/customers")
@RequiredArgsConstructor
@Tag(name = "Customers (dashboard)", description = "Read-only customer lookup and statements for the ops dashboard.")
public class DashboardCustomerController {

    private final CustomerService customerService;
    private final MerchantService merchantService;

    @Operation(summary = "Get a customer", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{reference}")
    public CyrusApiResponse<CustomerResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer retrieved",
                customerService.getByReference(merchantId, reference));
    }

    @Operation(
            summary = "Get a customer's statement",
            description = "Identity, a reporting summary (always over the customer's full history), and a " +
                    "paginated, newest-first transaction history — optionally narrowed with `from`/`to` " +
                    "(ISO-8601 instants) and/or `matchStatus`.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @GetMapping("/{reference}/statement")
    public CyrusApiResponse<CustomerStatementResponse> getStatement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reference,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) MatchStatus matchStatus,
            @PageableDefault(size = 20) Pageable pageable) {

        UUID merchantId = merchantService.findByBusinessEmail(jwt.getSubject()).getId();
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Statement retrieved",
                customerService.getStatement(merchantId, reference, from, to, matchStatus, pageable));
    }
}
