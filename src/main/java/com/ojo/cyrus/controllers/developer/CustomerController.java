package com.ojo.cyrus.controllers.developer;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerRequest;
import com.ojo.cyrus.models.requests.UpdateCustomerStatusRequest;
import com.ojo.cyrus.models.requests.UpdateKycTierRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CustomerStatementResponse;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Create and manage customers. Each customer is provisioned a dedicated virtual account.")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(
            summary = "Create a customer",
            description = "Creates a customer record and immediately provisions a dedicated virtual account via your connected payment provider.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @PostMapping
    public CyrusApiResponse<CustomerResponse> create(@AuthenticationPrincipal Merchant merchant, @RequestAttribute("ENVIRONMENT") Environment environment,
            @Valid @RequestBody CreateCustomerRequest request) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Customer created",
                customerService.create(merchant.getId(), environment, request));
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
            description = "Retrieves the customer's identity summary, lifetime received volume (kobo), " +
                    "and a paginated, newest-first history of transactions into their dedicated virtual account.",
            security = @SecurityRequirement(name = "ApiKeyAuth")
    )
    @GetMapping("/{reference}/statement")
    public CyrusApiResponse<CustomerStatementResponse> getStatement(
            @AuthenticationPrincipal Merchant merchant,
            @PathVariable String reference,
            @PageableDefault(size = 20) Pageable pageable) {

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Statement retrieved",
                customerService.getStatement(merchant.getId(), reference, pageable));
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
