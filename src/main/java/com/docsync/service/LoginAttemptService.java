package com.docsync.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tracks failed login attempts per email address using a Caffeine in-memory cache.
 * Enforces a lockout after {@value #MAX_ATTEMPTS} consecutive failures.
 *
 * <p>The Caffeine cache is configured with {@code expireAfterWrite(15, MINUTES)}.
 * Once an account is locked (count &ge; {@value #MAX_ATTEMPTS}), subsequent login
 * attempts are rejected before BCrypt is invoked, so no further writes occur.
 * The lock therefore naturally expires 15 minutes after the 5th failure write
 * (FINDING-018).
 *
 * <p>Per FINDING-002 / ADR-006: {@link #recordFailure(String)} must be called for
 * BOTH "email not found" and "password mismatch" login failure branches in
 * {@code AuthService}, preventing email-existence enumeration.
 */
@Service
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final String METRIC_FAILED = "login.attempts.failed";
    private static final String METRIC_LOCKED = "login.attempts.locked";

    private final Cache<String, AtomicInteger> attemptsCache;
    private final MeterRegistry meterRegistry;

    /**
     * Constructs a {@code LoginAttemptService} with the given metrics registry.
     * Initialises the Caffeine cache with a 15-minute write-expiry policy.
     *
     * @param meterRegistry Micrometer meter registry for emitting custom metrics
     */
    public LoginAttemptService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();
    }

    /**
     * Records a failed login attempt for the given email address.
     * Increments the attempt counter and emits the {@code login.attempts.failed} metric.
     * If the attempt count reaches {@value #MAX_ATTEMPTS}, also emits
     * {@code login.attempts.locked} and logs a warning.
     *
     * <p>This method must be called for BOTH "email not found" AND "password mismatch"
     * failure branches to prevent user-enumeration attacks (FINDING-002).
     *
     * @param email the email address for which a login failure occurred
     */
    public void recordFailure(String email) {
        AtomicInteger counter = attemptsCache.get(email, k -> new AtomicInteger(0));
        int attempts = counter.incrementAndGet();
        meterRegistry.counter(METRIC_FAILED).increment();
        if (attempts >= MAX_ATTEMPTS) {
            LOG.warn("Account locked due to {} failed login attempts for email hash: {}",
                attempts, email.hashCode());
            meterRegistry.counter(METRIC_LOCKED).increment();
        }
    }

    /**
     * Resets the failed login attempt counter for the given email address.
     * Must be called on every successful login (ADR-006).
     *
     * @param email the email address whose counter should be reset
     */
    public void resetAttempts(String email) {
        attemptsCache.invalidate(email);
    }

    /**
     * Returns whether the account for the given email address is currently locked.
     * An account is locked when its failure count is &ge; {@value #MAX_ATTEMPTS}.
     *
     * @param email the email address to check
     * @return {@code true} if the account is locked, {@code false} otherwise
     */
    public boolean isLocked(String email) {
        AtomicInteger counter = attemptsCache.getIfPresent(email);
        return counter != null && counter.get() >= MAX_ATTEMPTS;
    }
}
