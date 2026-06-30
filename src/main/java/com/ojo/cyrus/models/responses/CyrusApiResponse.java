package com.ojo.cyrus.models.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ojo.cyrus.enums.ResponseCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CyrusApiResponse<T>(
        @Schema(example = "00", description = "Response code")
        String code,

        @Schema(example = "Success", description = "Short description of the response code")
        String description,

        @Schema(example = "Operation completed successfully", description = "Detailed message")
        String message,

        @Schema(example = "true", description = "Status of the operation")
        boolean status,

        T data,

        @Schema(example = "2024-03-20T10:00:00Z", description = "Timestamp of the response")
        Instant timestamp) {

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