package com.ojo.cyrus.nomba;

import java.time.Instant;

record NombaTokenEntry(String accessToken, String refreshToken, Instant expiresAt) {

    boolean isValid() {
        return Instant.now().isBefore(expiresAt.minusSeconds(300));
    }
}
