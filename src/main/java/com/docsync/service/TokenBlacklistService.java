package com.docsync.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.index.qual.NonNegative;
import org.springframework.stereotype.Service;

/**
 * Maintains an in-memory set of invalidated (blacklisted) access tokens.
 * Uses a Caffeine cache with a custom expiry that auto-evicts each entry when the
 * corresponding token's own {@code exp} claim is reached, preventing unbounded memory growth
 * (ADR-002).
 *
 * <p>Cache entries are keyed by the raw token string. The value is the token's own
 * expiry time as a {@link Long} (epoch millis), enabling the custom expiry policy to
 * calculate per-entry TTLs correctly.
 *
 * <p>Migration path to Redis: extract this class to an interface and add a
 * {@code RedisTokenBlacklistService} implementation activated via
 * {@code @Profile("multi-instance")} (ADR-002).
 */
@Service
public class TokenBlacklistService {

    private final Cache<String, Long> blacklistCache;

    /**
     * Constructs a {@code TokenBlacklistService}.
     * Initialises the Caffeine cache with a custom {@link TokenExpiry} policy that
     * sets each entry's TTL to the remaining lifetime of the corresponding access token.
     */
    public TokenBlacklistService() {
        this.blacklistCache = Caffeine.newBuilder()
            .expireAfter(new TokenExpiry())
            .build();
    }

    /**
     * Adds the given access token to the blacklist.
     * The cache entry will be automatically evicted when {@code tokenExpiry} passes.
     *
     * @param token       the raw JWT access token string to blacklist
     * @param tokenExpiry the {@code exp} date of the token; the cache entry expires at this time
     */
    public void blacklist(String token, Date tokenExpiry) {
        blacklistCache.put(token, tokenExpiry.getTime());
    }

    /**
     * Returns whether the given access token is currently blacklisted.
     *
     * @param token the raw JWT access token string to check
     * @return {@code true} if the token has been blacklisted, {@code false} otherwise
     */
    public boolean isBlacklisted(String token) {
        return blacklistCache.getIfPresent(token) != null;
    }

    /**
     * Custom Caffeine {@link Expiry} that sets each cache entry's TTL to the remaining
     * lifetime of the associated token.
     * The stored value is the token's expiry time in epoch milliseconds.
     */
    private static final class TokenExpiry implements Expiry<String, Long> {

        /** Minimum TTL in nanoseconds (1 second) to avoid immediate eviction near expiry. */
        private static final long MIN_TTL_NANOS = TimeUnit.SECONDS.toNanos(1);

        @Override
        public long expireAfterCreate(String key, Long expiryEpochMillis, long currentTime) {
            long remainingMs = expiryEpochMillis - System.currentTimeMillis();
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(remainingMs);
            return Math.max(remainingNanos, MIN_TTL_NANOS);
        }

        @Override
        public long expireAfterUpdate(String key, Long value,
                long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }

        @Override
        public long expireAfterRead(String key, Long value,
                long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }
    }
}
