package com.ojo.cyrus.utils;

import lombok.experimental.UtilityClass;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.security.SecureRandom;

@UtilityClass
public  class CryptoUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(value.getBytes());

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hashing failed", e);
        }

    }
    public static String randomToken(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

    }

    public static String hmacSha256(String payload, String secret) {
            return "";
    }
}
