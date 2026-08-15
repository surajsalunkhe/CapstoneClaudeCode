package com.docsync.service;

import com.docsync.repository.RefreshTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task that removes expired refresh tokens from the database.
 * Runs hourly to prevent unbounded growth of the {@code refresh_tokens} table
 * (FINDING-016 / architecture risk mitigation).
 */
@Component
public class RefreshTokenCleanupTask {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenCleanupTask.class);

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Constructs a {@code RefreshTokenCleanupTask} with the required repository.
     *
     * @param refreshTokenRepository repository used to delete expired tokens
     */
    public RefreshTokenCleanupTask(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Deletes all refresh tokens that have expired. Runs hourly (cron: every hour on the hour).
     * The number of deleted records is logged at INFO level.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        LOG.info("Refresh token cleanup: deleted {} expired tokens", deleted);
    }
}
