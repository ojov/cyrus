package com.ojo.cyrus.models.responses;

import java.util.UUID;

public record VirtualAccountResponse(

        UUID id,
        String customerReference,
        String accountNumber,

        String accountName,

        String bankName,

        String status

) {}