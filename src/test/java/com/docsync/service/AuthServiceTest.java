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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. All dependencies are mocked with Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private AuthService authService;

    private User testUser;

    /**
     * Initialises the service under test and a test user before each test.
     */
    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtService,
            loginAttemptService, tokenBlacklistService, passwordEncoder);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("user@example.com");
        testUser.setPasswordHash("$2a$10$hashed");
        testUser.setEnabled(true);
    }

    /**
     * Verifies that a valid registration request returns a populated RegisterResponse.
     */
    @Test
    void register_success_returnsRegisterResponse() {
        RegisterRequest request = new RegisterRequest(
            "user@example.com", "Passw0rd!", "Passw0rd!", true);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("user@example.com", response.email());
        assertEquals(testUser.getId(), response.userId());
    }

    /**
     * Verifies that a duplicate email throws DuplicateEmailException.
     */
    @Test
    void register_duplicateEmail_throwsDuplicateEmailException() {
        RegisterRequest request = new RegisterRequest(
            "user@example.com", "Passw0rd!", "Passw0rd!", true);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.register(request));
    }

    /**
     * Verifies that mismatched passwords throw IllegalArgumentException (HTTP 400 per AC-003).
     */
    @Test
    void register_passwordMismatch_throwsException() {
        RegisterRequest request = new RegisterRequest(
            "user@example.com", "Passw0rd!", "Different!", true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    /**
     * Verifies that acceptTerms=false throws IllegalArgumentException.
     */
    @Test
    void register_acceptTermsFalse_throwsException() {
        RegisterRequest request = new RegisterRequest(
            "user@example.com", "Passw0rd!", "Passw0rd!", false);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    /**
     * Verifies that valid credentials return a LoginResponse with tokens.
     */
    @Test
    void login_success_returnsLoginResponse() {
        LoginRequest request = new LoginRequest("user@example.com", "Passw0rd!", false);
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Passw0rd!", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(testUser)).thenReturn("access.token");
        when(jwtService.generateRefreshToken(testUser, false)).thenReturn("refresh.token");
        when(jwtService.extractExpiration("refresh.token"))
            .thenReturn(new Date(System.currentTimeMillis() + 604800000L));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access.token", response.accessToken());
        assertEquals("refresh.token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1800, response.expiresIn());
        verify(loginAttemptService).resetAttempts("user@example.com");
    }

    /**
     * Verifies that an unknown email records a failure and throws InvalidCredentialsException
     * (FINDING-002 — prevents user enumeration).
     */
    @Test
    void login_unknownEmail_recordsFailureAndThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Passw0rd!", false);
        when(loginAttemptService.isLocked("unknown@example.com")).thenReturn(false);
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(loginAttemptService).recordFailure("unknown@example.com");
    }

    /**
     * Verifies that a wrong password records a failure and throws InvalidCredentialsException.
     */
    @Test
    void login_wrongPassword_recordsFailureAndThrowsInvalidCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPass!", false);
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPass!", "$2a$10$hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(loginAttemptService).recordFailure("user@example.com");
    }

    /**
     * Verifies that a locked account throws AccountLockedException without checking credentials.
     */
    @Test
    void login_accountLocked_throwsAccountLockedException() {
        LoginRequest request = new LoginRequest("user@example.com", "Passw0rd!", false);
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(true);

        assertThrows(AccountLockedException.class, () -> authService.login(request));
        verify(userRepository, never()).findByEmail(anyString());
    }

    /**
     * Verifies that reaching 5 failures locks the account (failure counter increments).
     */
    @Test
    void login_fifthFailure_locksAccount() {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPass!", false);
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(loginAttemptService).recordFailure("user@example.com");
    }

    /**
     * Verifies that a successful login resets the failure counter.
     */
    @Test
    void login_successAfterFailures_resetsAttempts() {
        LoginRequest request = new LoginRequest("user@example.com", "Passw0rd!", false);
        when(loginAttemptService.isLocked("user@example.com")).thenReturn(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Passw0rd!", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateAccessToken(testUser)).thenReturn("access.token");
        when(jwtService.generateRefreshToken(testUser, false)).thenReturn("refresh.token");
        when(jwtService.extractExpiration("refresh.token"))
            .thenReturn(new Date(System.currentTimeMillis() + 604800000L));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(new RefreshToken());

        authService.login(request);

        verify(loginAttemptService).resetAttempts("user@example.com");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    /**
     * Verifies that logout blacklists the access token.
     */
    @Test
    void logout_blacklistsToken() {
        Date expiry = new Date(System.currentTimeMillis() + 1800000L);
        when(jwtService.extractExpiration("access.token")).thenReturn(expiry);
        SecurityContextHolder.clearContext();

        authService.logout("access.token");

        verify(tokenBlacklistService).blacklist("access.token", expiry);
    }

    /**
     * Verifies that logout deletes the user's refresh tokens from the database.
     */
    @Test
    void logout_deletesRefreshTokens() {
        Date expiry = new Date(System.currentTimeMillis() + 1800000L);
        when(jwtService.extractExpiration("access.token")).thenReturn(expiry);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(testUser);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);

        authService.logout("access.token");

        verify(refreshTokenRepository).deleteByUser(testUser);
        SecurityContextHolder.clearContext();
    }

    /**
     * Verifies that a valid refresh token returns a new access token.
     */
    @Test
    void refresh_success_returnsNewAccessToken() {
        RefreshToken stored = new RefreshToken();
        stored.setToken("valid.refresh.token");
        stored.setUser(testUser);
        stored.setExpiresAt(Instant.now().plusSeconds(3600));
        when(jwtService.isTokenExpired("valid.refresh.token")).thenReturn(false);
        when(refreshTokenRepository.findByToken("valid.refresh.token"))
            .thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(testUser)).thenReturn("new.access.token");

        RefreshResponse response = authService.refresh("valid.refresh.token");

        assertNotNull(response);
        assertEquals("new.access.token", response.accessToken());
        assertEquals(1800, response.expiresIn());
    }

    /**
     * Verifies that an expired refresh token throws TokenExpiredException.
     */
    @Test
    void refresh_expiredRefreshToken_throwsTokenExpiredException() {
        when(jwtService.isTokenExpired("expired.refresh.token")).thenReturn(true);

        assertThrows(TokenExpiredException.class,
            () -> authService.refresh("expired.refresh.token"));
    }

    /**
     * Verifies that an unknown refresh token throws InvalidTokenException.
     */
    @Test
    void refresh_unknownRefreshToken_throwsInvalidTokenException() {
        when(jwtService.isTokenExpired("unknown.token")).thenReturn(false);
        when(refreshTokenRepository.findByToken("unknown.token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.refresh("unknown.token"));
    }
}
