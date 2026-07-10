package com.ojo.cyrus.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically cleans up revoked and expired refresh tokens from the database.
 * Runs daily at 3 AM UTC to minimize impact on peak hours.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupService {

    private final TokenService tokenService;

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")
    public void cleanupExpiredTokens() {
        int deleted = tokenService.cleanupExpiredRefreshTokens();
        if (deleted > 0) {
            log.info("Cleaned up {} revoked or expired refresh tokens", deleted);
        }
    }
}
