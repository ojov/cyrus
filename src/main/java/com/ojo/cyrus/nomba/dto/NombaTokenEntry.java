package com.ojo.cyrus.nomba.dto;

import java.time.Instant;

public record NombaTokenEntry(String accessToken, String refreshToken, Instant expiresAt) {

    public boolean isValid() {
        return Instant.now().isBefore(expiresAt.minusSeconds(300));
    }
}
