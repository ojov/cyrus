package com.ojo.cyrus.models.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ojo.cyrus.enums.ResponseCode;
import lombok.Builder;

import java.time.Instant;
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CyrusApiResponse<T>(String code, String description,
        String message, boolean status, T data, Instant timestamp) {

    public static <T> CyrusApiResponse<T> success(ResponseCode responseCode, String message, T data){
        return new CyrusApiResponse<>(
                responseCode.getCode(),
                responseCode.getDescription(),
                responseCode.getDescription(),
                true,
                data,
                Instant.now()
        );
    }


    public static <T> CyrusApiResponse<T> failure(ResponseCode responseCode, String message) {
        return new CyrusApiResponse<>(
                responseCode.getCode(),
                responseCode.getDescription(),
                message,
                false,
                null,
                Instant.now()
        );
    }
}