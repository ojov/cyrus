package com.ojo.cyrus.controllers;

import com.ojo.cyrus.models.requests.CreateVirtualAccountRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.VirtualAccountResponse;
import com.ojo.cyrus.services.VirtualAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/virtual-accounts")
@RequiredArgsConstructor
@Tag(name = "Virtual Accounts", description = "Endpoints for managing virtual accounts via API Key")
public class VirtualAccountController {


    private final VirtualAccountService virtualAccountService;


    @Operation(
            summary = "Create a virtual account",
            description = "Creates a new virtual account for a customer. Requires an API Key in the Authorization header.",
            security = @SecurityRequirement(name = "ApiKeyAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Virtual account created successfully"),
                    @ApiResponse(responseCode = "401", description = "Invalid or missing API Key"),
                    @ApiResponse(responseCode = "403", description = "Authenticated merchant is not allowed to perform this action")
            }
    )
    @PostMapping
    public CyrusApiResponse<VirtualAccountResponse> create(@AuthenticationPrincipal Merchant merchant,
            @RequestBody CreateVirtualAccountRequest request){

        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Virtual Account Created Successfully",
                virtualAccountService.create(merchant, request));
    }
}