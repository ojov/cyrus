package com.ojo.cyrus.enums;

public enum Provider {
    NOMBA(
            "https://sandbox.nomba.com",
            "https://api.nomba.com"),

    PAYSTACK(
            "https://api.paystack.co",
            "https://api.paystack.co"),

    MONIEPOINT(
            "https://sandbox-api.moniepoint.com",
            "https://api.moniepoint.com");

    private final String sandboxBaseUrl;
    private final String productionBaseUrl;

    Provider(String sandboxBaseUrl, String productionBaseUrl) {
        this.sandboxBaseUrl = sandboxBaseUrl;
        this.productionBaseUrl = productionBaseUrl;
    }

    public String getBaseUrl(Environment environment) {
        return switch (environment) {
            case TEST -> sandboxBaseUrl;
            case LIVE -> productionBaseUrl;
        };
    }
}