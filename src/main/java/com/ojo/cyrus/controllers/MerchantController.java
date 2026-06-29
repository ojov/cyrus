package com.ojo.cyrus.controllers;

import com.ojo.cyrus.common.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@Tag(name = "Merchant Operations", description = "Create, perform merchant operations")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;

    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public CyrusApiResponse<MerchantRegistrationResponse> register(@Valid @RequestBody MerchantRegistrationRequest request){
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", merchantService.register(request));

    }

}
