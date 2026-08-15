package com.docsync.service;

import com.docsync.exception.AccountLockedException;
import com.docsync.exception.DuplicateEmailException;
import com.docsync.exception.InvalidCredentialsException;
import com.docsync.exception.InvalidTokenException;
import com.docsync.exception.TokenExpiredException;
import com.docsync.model.entity.RefreshToken;
import com.docsync.model.entity.User;
import com.docsync.model.request.LoginRequest;
import com.docsync.model.request.RegisterRequest;
import com.docsync.model.response.LoginResponse;
import com.docsync.model.response.RefreshResponse;
import com.docsync.model.response.RegisterResponse;
import com.docsync.repository.RefreshTokenRepository;
import com.docsync.repository.UserRepository;
import java.time.Instant;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core authentication service that orchestrates registration, login, logout,
 * and token refresh flows.
 *
 * <p>This service enforces all business rules:
 * <ul>
 *   <li>Password confirmation and terms acceptance on registration</li>
 *   <li>Duplicate email check on registration</li>
 *   <li>Account lockout enforcement and failed-attempt tracking on login</li>
 *   <li>Access token blacklisting and refresh token revocation on logout</li>
 *   <li>Refresh token expiry and existence validation on token refresh</li>
 * </ul>
 *
 * <p>Passwords are never written to any log statement.
 */
@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password";
    private static final int ACCESS_TOKEN_EXPIRES_IN_SECONDS = 1800;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Constructs an {@code AuthService} with all required dependencies.
     *
     * @param userRepository         repository for user persistence
     * @param refreshTokenRepository repository for refresh token persistence
     * @param jwtService             service for JWT generation and validation
     * @param loginAttemptService    service for tracking and enforcing login lockout
     * @param tokenBlacklistService  service for access token revocation
     * @param passwordEncoder        BCrypt encoder for password hashing and verification
     */
    public AuthService(UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            TokenBlacklistService tokenBlacklistService,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user account.
     *
     * <p>Validates that passwords match, terms are accepted, and the email is not already
     * registered. Hashes the password with BCrypt before persisting.
     *
     * @param request the registration request containing email, password, confirmPassword,
     *                and acceptTerms
     * @return a {@link RegisterResponse} containing the new user's ID, email, and creation time
     * @throws IllegalArgumentException    if {@code confirmPassword} does not match
     *                                     {@code password}, or if {@code acceptTerms} is
     *                                     {@code false}
     * @throws DuplicateEmailException     if the email address is already registered
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (!request.acceptTerms()) {
            throw new IllegalArgumentException("Terms must be accepted");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException("Email already registered");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        LOG.info("User registered successfully with email hash: {}", request.email().hashCode());
        return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getCreatedAt());
    }

    /**
     * Authenticates a user and issues JWT access and refresh tokens.
     *
     * <p>Flow:
     * <ol>
     *   <li>Check if the account is locked (throws {@link AccountLockedException})</li>
     *   <li>Look up the user by email; record a failure and throw
     *       {@link InvalidCredentialsException} if not found (FINDING-002)</li>
     *   <li>Verify the password; record a failure and throw
     *       {@link InvalidCredentialsException} if incorrect</li>
     *   <li>Reset the failure counter on success (ADR-006)</li>
     *   <li>Generate tokens and persist the refresh token</li>
     * </ol>
     *
     * @param request the login request containing email, password, and rememberMe flag
     * @return a {@link LoginResponse} with access token, refresh token, and user details
     * @throws AccountLockedException      if the account is temporarily locked
     * @throws InvalidCredentialsException if the email is unknown or the password is incorrect
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email();
        if (loginAttemptService.isLocked(email)) {
            throw new AccountLockedException(
                "Account locked due to too many failed attempts. Try again in 15 minutes.");
        }
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MSG);
        });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MSG);
        }
        loginAttemptService.resetAttempts(email);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = jwtService.generateRefreshToken(user, request.rememberMe());
        Date refreshExpiry = jwtService.extractExpiration(refreshTokenValue);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(refreshExpiry.toInstant());
        refreshTokenRepository.save(refreshToken);
        LOG.info("User authenticated successfully, email hash: {}", email.hashCode());
        return new LoginResponse(
            accessToken,
            refreshTokenValue,
            "Bearer",
            ACCESS_TOKEN_EXPIRES_IN_SECONDS,
            user.getId(),
            user.getEmail()
        );
    }

    /**
     * Logs out the authenticated user by blacklisting their access token and
     * revoking all their active refresh tokens.
     *
     * @param accessToken the raw JWT access token from the {@code Authorization} header
     */
    @Transactional
    public void logout(String accessToken) {
        Date expiry = jwtService.extractExpiration(accessToken);
        tokenBlacklistService.blacklist(accessToken, expiry);
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
            ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
            : null;
        if (principal instanceof User user) {
            refreshTokenRepository.deleteByUser(user);
            LOG.info("User logged out, email hash: {}", user.getEmail().hashCode());
        }
    }

    /**
     * Issues a new access token in exchange for a valid, non-expired refresh token.
     *
     * @param refreshTokenValue the raw JWT refresh token string
     * @return a {@link RefreshResponse} containing the new access token and expiry
     * @throws TokenExpiredException if the refresh token has expired
     * @throws InvalidTokenException if the refresh token is not found in the database
     */
    @Transactional
    public RefreshResponse refresh(String refreshTokenValue) {
        if (jwtService.isTokenExpired(refreshTokenValue)) {
            throw new TokenExpiredException("Token has expired");
        }
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
            .orElseThrow(() -> new InvalidTokenException("Invalid or revoked token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenExpiredException("Token has expired");
        }
        String newAccessToken = jwtService.generateAccessToken(stored.getUser());
        return new RefreshResponse(newAccessToken, ACCESS_TOKEN_EXPIRES_IN_SECONDS);
    }
}
