package com.ojo.cyrus.controllers.developer;

import com.ojo.cyrus.enums.MatchStatus;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerStatusRequest;
import com.ojo.cyrus.models.requests.UpdateKycTierRequest;
import com.ojo.cyrus.models.responses.CustomerListItemResponse;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Create and manage customers. Each customer is provisioned a dedicated virtual account.")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(
            summary = "Create a customer",
            description = "Creates a customer record and immediately provisions a dedicated virtual account for your customer to make payments to you.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PostMapping
    public CyrusApiResponse<CustomerResponse> create(@AuthenticationPrincipal Merchant merchant,
            @Valid @RequestBody CreateCustomerRequest request) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer created",
                customerService.create(merchant.getId(), request));
    }

    @Operation(
            summary = "List customers",
            description = "Paginated, newest-first list of every customer you've provisioned, each with their " +
                    "virtual account and lifetime received volume (SUCCESSFUL customer payments).",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping
    public CyrusApiResponse<Page<CustomerListItemResponse>> list(
            @AuthenticationPrincipal Merchant merchant,
            @PageableDefault(size = 20) Pageable pageable) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customers retrieved",
                customerService.list(merchant.getId(), pageable));
    }

    @Operation(
            summary = "Get a customer",
            description = "Retrieves a customer and their dedicated virtual account by your reference.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping("/{reference}")
    public CyrusApiResponse<CustomerResponse> get(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer retrieved",
                customerService.getByReference(merchant.getId(), reference));
    }

    @Operation(
            summary = "Get a customer's statement",
            description = "Retrieves the customer's identity, a reporting summary (lifetime received volume — " +
                    "the net amount credited to your wallet after Nomba's and Cyrus's fees are deducted, " +
                    "transaction/pending counts, manual-review/discrepancy counts, last transaction date — " +
                    "always over the customer's full history), and a paginated, newest-first transaction history. " +
                    "The list can be narrowed with `from`/`to` (ISO-8601 instants) and/or `matchStatus` — e.g. " +
                    "`matchStatus=DISCREPANCY` to pull up just the exception rows. The summary is unaffected by " +
                    "these filters.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping("/{reference}/statement")
    public CyrusApiResponse<CustomerStatementResponse> getStatement(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) MatchStatus matchStatus,
            @PageableDefault(size = 20) Pageable pageable) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Statement retrieved",
                customerService.getStatement(merchant.getId(), reference, from, to, matchStatus, pageable));
    }

    @Operation(
            summary = "Update a customer's profile",
            description = "Partial update of firstName/lastName/email/phoneNumber. Your reference stays fixed. " +
                    "A firstName/lastName change also renames the virtual account's bank account name to match.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PatchMapping("/{reference}")
    public CyrusApiResponse<CustomerResponse> update(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference,
            @Valid @RequestBody UpdateCustomerRequest request) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer updated",
                customerService.rename(merchant.getId(), reference, request));
    }

    @Operation(
            summary = "Set a customer's KYC tier",
            description = "Cyrus doesn't verify KYC itself — call this whenever your own verification process " +
                    "completes. The virtual account is unaffected by a tier change.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PostMapping("/{reference}/kyc-tier")
    public CyrusApiResponse<CustomerResponse> updateKycTier(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference,
            @Valid @RequestBody UpdateKycTierRequest request) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "KYC tier updated",
                customerService.updateKycTier(merchant.getId(), reference, request.tier()));
    }

    @Operation(
            summary = "Suspend, reactivate, or close a customer",
            description = "Cascades to the customer's virtual account. ACTIVE/SUSPENDED are freely reversible; " +
                    "CLOSED is terminal — the customer and their transaction history stay intact, but no further " +
                    "status change is accepted. A payment landing on a non-ACTIVE virtual account is recorded as " +
                    "an orphan (not attributed to the customer) and is recoverable via the payment-events reattribute endpoint.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PatchMapping("/{reference}/status")
    public CyrusApiResponse<CustomerResponse> updateStatus(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference,
            @Valid @RequestBody UpdateCustomerStatusRequest request) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer status updated",
                customerService.updateStatus(merchant.getId(), reference, request.status()));
    }
}
