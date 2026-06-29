package com.ojo.cyrus.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    SUCCESS("00", "SUCCESS"),
    CREATED("01", "CREATED"),

    INVALID_INPUT("70", "INVALID_INPUT"),
    INVALID_REQUEST("71", "INVALID_REQUEST"),

    MERCHANT_NOT_FOUND("80", "MERCHANT_NOT_FOUND"),
    DUPLICATE_MERCHANT("81", "DUPLICATE_MERCHANT"),

    INTERNAL_ERROR("99", "INTERNAL_ERROR");

    private final String code;
    private final String description;
}