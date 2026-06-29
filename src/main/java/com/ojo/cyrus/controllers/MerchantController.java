package com.ojo.cyrus.controllers;

import com.ojo.cyrus.common.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.RegisterMerchantRequest;
import com.ojo.cyrus.models.responses.RegisterMerchantResponse;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@Tag(name = "Merchant Operations", description = "Create, perform merchant operations")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;

    @PostMapping("/register")
    public CyrusApiResponse<RegisterMerchantResponse> register( @Valid @RequestBody RegisterMerchantRequest request){
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", merchantService.register(request));

    }

}
