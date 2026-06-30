package com.ojo.cyrus.controllers;

import com.ojo.cyrus.models.responses.CyrusApiResponse;
import com.ojo.cyrus.enums.ResponseCode;
import com.ojo.cyrus.models.requests.LoginRequest;
import com.ojo.cyrus.models.requests.MerchantRegistrationRequest;
import com.ojo.cyrus.models.responses.LoginResponse;
import com.ojo.cyrus.models.responses.MerchantRegistrationResponse;
import com.ojo.cyrus.services.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login with business email and password")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public CyrusApiResponse<MerchantRegistrationResponse> register(@Valid @RequestBody MerchantRegistrationRequest request){
        return CyrusApiResponse.success(ResponseCode.CREATED, "Merchant Created Successfully", authService.register(request));
    }

    @PostMapping("/login")
    public CyrusApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return CyrusApiResponse.success(ResponseCode.SUCCESS, "Login successful", authService.login(request));
    }
}
