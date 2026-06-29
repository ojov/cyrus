package com.ojo.cyrus.models.responses;

import lombok.Builder;

import java.util.UUID;
@Builder
public record MerchantRegistrationResponse(
        UUID merchantId,
        String apiKey
) {}