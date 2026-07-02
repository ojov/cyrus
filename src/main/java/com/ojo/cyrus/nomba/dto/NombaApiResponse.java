package com.ojo.cyrus.nomba.dto;

public record NombaApiResponse<T>(String code, String description, String message, T data) {

    public boolean isSuccess() {
        return "00".equals(code);
    }
}
