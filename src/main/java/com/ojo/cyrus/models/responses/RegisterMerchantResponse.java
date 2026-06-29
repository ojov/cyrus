package com.ojo.cyrus.models.responses;

import java.util.UUID;

public record RegisterMerchantResponse(
        UUID merchantId,
        String apiKey
) {}