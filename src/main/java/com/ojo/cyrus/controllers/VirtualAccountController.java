package com.ojo.cyrus.controllers;

import com.ojo.cyrus.models.requests.CreateVirtualAccountRequest;
import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.entities.Merchant;
import com.ojo.cyrus.models.responses.VirtualAccountResponse;
import com.ojo.cyrus.services.ApiKeyService;
import com.ojo.cyrus.services.VirtualAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/virtual-accounts")
@RequiredArgsConstructor
public class VirtualAccountController {


    private final VirtualAccountService service;


    @PostMapping
    public CyrusApiResponse<VirtualAccountResponse> create(
            @AuthenticationPrincipal Merchant merchant,
            @RequestBody CreateVirtualAccountRequest request
    ){

        return CyrusApiResponse.success(
                ResponseCode.SUCCESS,
                "Virtual Account Created Successfully",
                service.create(
                        merchant,
                        request
                )
        );
    }
}