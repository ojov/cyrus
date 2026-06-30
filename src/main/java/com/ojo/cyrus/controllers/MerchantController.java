package com.ojo.cyrus.controllers;

import com.ojo.cyrus.services.MerchantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/merchants")
@Tag(name = "Merchant Operations", description = "Create, perform merchant operations")
@RequiredArgsConstructor
public class MerchantController {
    private final MerchantService merchantService;


}
