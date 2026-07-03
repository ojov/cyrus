package com.ojo.cyrus.controllers;

import com.ojo.cyrus.enums.Environment;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.requests.CreateCustomerRequest;
import com.ojo.cyrus.models.responses.CustomerResponse;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.services.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
