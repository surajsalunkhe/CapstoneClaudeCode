package com.docsync.service;

import com.docsync.repository.RefreshTokenRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenCleanupTask}.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupTaskTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenCleanupTask cleanupTask;

    /**
     * Initialises the task under test before each test.
     */
    @BeforeEach
    void setUp() {
        cleanupTask = new RefreshTokenCleanupTask(refreshTokenRepository);
    }

    /**
     * Verifies that deleteExpiredTokens calls the repository with an Instant
     * at or before the current time.
     */
    @Test
    void deleteExpiredTokens_callsRepositoryWithCurrentTime() {
        Instant before = Instant.now();
        when(refreshTokenRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(0);

        cleanupTask.deleteExpiredTokens();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteAllExpiredBefore(captor.capture());
        Instant usedInstant = captor.getValue();
        assertNotNull(usedInstant);
        assertTrue(!usedInstant.isBefore(before));
    }

    /**
     * Verifies that deleteExpiredTokens calls the repository method exactly once.
     */
    @Test
    void deleteExpiredTokens_logsDeletedCount() {
        when(refreshTokenRepository.deleteAllExpiredBefore(any(Instant.class))).thenReturn(5);

        cleanupTask.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteAllExpiredBefore(any(Instant.class));
    }
}
