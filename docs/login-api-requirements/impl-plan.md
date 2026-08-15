# Implementation Plan: Login API

**Status**: DONE
**Version**: 1.0
**Date**: 2026-08-15
**Requirements**: `docs/login-api-requirements/requirements.md` (APPROVED)
**Architecture**: `architecture.md` (PENDING HUMAN APPROVAL)
**Design Review**: `docs/login-api-requirements/design-review.md` (APPROVED WITH CHANGES REQUIRED)

> **NOTE**: This plan incorporates all Required Changes (RC-001 through RC-008) from the design review. The architecture.md must be updated and human-approved before implementation begins.

---

## Dependency Order Summary

```
TASK-001 (Maven/POM)
  └─► TASK-002 (application.yml + Flyway migration) [RC-006]
        └─► TASK-003 (Entities: User, RefreshToken)
              └─► TASK-004 (Repositories: UserRepository, RefreshTokenRepository)
                    └─► TASK-005 (Custom Exceptions)
                          ├─► TASK-006 (JwtProperties + startup validation) [RC-001 partial]
                          │     └─► TASK-007 (JwtService)
                          ├─► TASK-008 (LoginAttemptService — Caffeine)
                          ├─► TASK-009 (TokenBlacklistService — Caffeine)
                          └─► TASK-010 (UserDetailsServiceImpl) [RC-002]
                                └─► TASK-011 (SecurityConfig + IpRateLimiterFilter) [RC-001, RC-004, RC-005]
                                      └─► TASK-012 (JwtAuthenticationFilter)
                                            └─► TASK-013 (RequestCorrelationFilter) [RC-001 partial]
                                                  └─► TASK-014 (AuthService)
                                                        └─► TASK-015 (AuthController)
                                                              └─► TASK-016 (GlobalExceptionHandler)
                                                                    └─► TASK-017 (RefreshTokenCleanupTask)
                                                                          └─► TASK-018 (Unit Tests)
                                                                                └─► TASK-019 (Integration Tests)
                                                                                      └─► TASK-020 (Verification)
```

---

## TASK-001 — Maven Project Setup

**Component**: Project Build  
**Dependencies**: None  
**Design Review RC**: None (foundation)

### Expected Files
- `pom.xml` (create or replace)

### Implementation Details

Configure `pom.xml` with:

```xml
<groupId>com.docsync</groupId>
<artifactId>login-api</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>
<java.version>17</java.version>
```

**Dependencies** (Spring Boot 3.x BOM manages versions unless pinned):

| Dependency | Scope | Notes |
|---|---|---|
| `spring-boot-starter-web` | compile | MVC + Tomcat |
| `spring-boot-starter-security` | compile | Spring Security 6 |
| `spring-boot-starter-data-jpa` | compile | JPA/Hibernate 6 |
| `spring-boot-starter-validation` | compile | Jakarta Bean Validation |
| `spring-boot-starter-actuator` | compile | Health + Metrics |
| `spring-boot-starter-aop` | compile | Required by Resilience4j |
| `postgresql` | runtime | PostgreSQL JDBC driver |
| `io.jsonwebtoken:jjwt-api:0.12.6` | compile | JWT API (ADR-004) |
| `io.jsonwebtoken:jjwt-impl:0.12.6` | runtime | JWT implementation |
| `io.jsonwebtoken:jjwt-jackson:0.12.6` | runtime | JWT Jackson integration |
| `com.github.ben-manes.caffeine:caffeine` | compile | In-memory cache (ADR-002) |
| `io.github.resilience4j:resilience4j-spring-boot3` | compile | Rate limiting (NFR-001.4) |
| `org.flywaydb:flyway-core` | compile | DB migrations (RC-006) |
| `org.flywaydb:flyway-database-postgresql` | compile | Flyway PostgreSQL support |
| `springdoc-openapi-starter-webmvc-ui:2.x` | compile | OpenAPI 3.0 (NFR-005.4) |
| `spring-boot-starter-test` | test | JUnit 5, Mockito, MockMvc |
| `org.testcontainers:postgresql` | test | Integration test DB (RC-008 related) |
| `org.testcontainers:junit-jupiter` | test | Testcontainers JUnit 5 support |

**Plugins**:
- `spring-boot-maven-plugin` — executable JAR
- `maven-checkstyle-plugin` — Google Java Style, fail on violation
- `spotbugs-maven-plugin` — fail on HIGH/MEDIUM
- `jacoco-maven-plugin` — enforce 80% instruction coverage

**Maven Profiles**:
- `integration-tests`: includes `*IT.java` and `*IntegrationTest.java` via Failsafe plugin

### Test Requirements
- Run `mvn compile` with no errors after setup.
- Run `mvn dependency:tree` to confirm all required dependencies resolve.

### Acceptance Criteria
- [ ] `mvn compile` exits 0.
- [ ] All declared dependencies resolve without conflict.
- [ ] JaCoCo, Checkstyle, and SpotBugs plugins are bound to the `verify` lifecycle.
- [ ] `integration-tests` profile exists and binds Failsafe.

---

## TASK-002 — Application Configuration and Flyway Migration

**Component**: Configuration / Database Migration  
**Dependencies**: TASK-001  
**Design Review RC**: RC-006 (Flyway), RC-004 (Swagger prod profile), RC-005 (Actuator access)

### Expected Files
- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/application-test.yml`
- `src/main/resources/db/migration/V1__create_auth_tables.sql`

### Implementation Details

**`application.yml`** (base configuration):

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  hikari:
    maximum-pool-size: 10
    connection-timeout: 3000   # RC-005 / FINDING-017: reduced from 30s to 3s

jwt:
  secret: ${JWT_SECRET}
  access-token-expiry-ms: 1800000
  refresh-token-expiry-ms: 604800000
  remember-me-expiry-ms: 2592000000

resilience4j:
  ratelimiter:
    instances:
      authRateLimiter:
        limit-for-period: 5
        limit-refresh-period: 60s
        timeout-duration: 0s

management:
  server:
    port: 8081    # RC-005: actuator on separate port
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: never

springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
```

**`application-prod.yml`** (RC-004 — disable Swagger in production):

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

**`application-test.yml`** (rate limiter test profile — FINDING-021):

```yaml
resilience4j:
  ratelimiter:
    instances:
      authRateLimiter:
        limit-for-period: 1
        limit-refresh-period: 10s
        timeout-duration: 0s
```

**`V1__create_auth_tables.sql`** (RC-006 — DDL from architecture section 7):

```sql
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token      TEXT        NOT NULL UNIQUE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

### Test Requirements
- Flyway migration runs cleanly against a Testcontainers PostgreSQL 15 instance.
- Verify `ddl-auto: validate` passes after migration.

### Acceptance Criteria
- [ ] `spring.flyway.enabled=true` and migration file in correct location.
- [ ] `application-prod.yml` disables Swagger.
- [ ] Actuator binds to port 8081.
- [ ] HikariCP `connection-timeout` is 3000 ms.
- [ ] Flyway migration creates both tables and all indexes.

---

## TASK-003 — JPA Entities

**Component**: `com.docsync.model.entity`  
**Dependencies**: TASK-002

### Expected Files
- `src/main/java/com/docsync/model/entity/User.java`
- `src/main/java/com/docsync/model/entity/RefreshToken.java`

### Implementation Details

**`User.java`** — JPA entity (mutable class, NOT a record):

```java
@Entity
@Table(name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // UserDetails overrides: getUsername() → email, getPassword() → passwordHash
    // getAuthorities() → [ROLE_USER], isAccountNonExpired/Locked/CredentialsNonExpired → true
    // isEnabled() → enabled
    // @PrePersist sets createdAt/updatedAt; @PreUpdate sets updatedAt
}
```

`User` implements `UserDetails` directly to avoid a separate adapter class (FINDING-007 / RC-002).

**`RefreshToken.java`**:

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### Test Requirements
- No dedicated unit test required for pure JPA entities.
- Verified indirectly by Flyway migration + `ddl-auto: validate` in TASK-002.

### Acceptance Criteria
- [ ] `User` implements `UserDetails`; `getUsername()` returns `email`.
- [ ] `User.isEnabled()` returns the `enabled` field.
- [ ] `@PrePersist` / `@PreUpdate` manage timestamps.
- [ ] `RefreshToken.user` is `FetchType.LAZY`.
- [ ] No raw types. No `System.out.println`. SLF4J only.

---

## TASK-004 — JPA Repositories

**Component**: `com.docsync.repository`  
**Dependencies**: TASK-003

### Expected Files
- `src/main/java/com/docsync/repository/UserRepository.java`
- `src/main/java/com/docsync/repository/RefreshTokenRepository.java`

### Implementation Details

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);  // for TASK-017 cleanup
}
```

### Test Requirements
- No unit tests for Spring Data interfaces (tested via integration tests in TASK-019).

### Acceptance Criteria
- [ ] `UserRepository` provides `findByEmail` and `existsByEmail`.
- [ ] `RefreshTokenRepository` provides `deleteByUser` and `deleteAllExpiredBefore`.
- [ ] No raw types.

---

## TASK-005 — Custom Exceptions

**Component**: `com.docsync.exception`  
**Dependencies**: TASK-001

### Expected Files
- `src/main/java/com/docsync/exception/DuplicateEmailException.java`
- `src/main/java/com/docsync/exception/InvalidCredentialsException.java`
- `src/main/java/com/docsync/exception/AccountLockedException.java`
- `src/main/java/com/docsync/exception/InvalidTokenException.java`
- `src/main/java/com/docsync/exception/TokenExpiredException.java`

### Implementation Details

All exceptions extend `RuntimeException`. Each takes a `String message` constructor parameter.

```java
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String message) { super(message); }
}
// Repeat pattern for each exception class
```

### Test Requirements
- Tested indirectly through `GlobalExceptionHandlerTest` (TASK-018).

### Acceptance Criteria
- [ ] All five exception classes present, extending `RuntimeException`.
- [ ] No checked exceptions.

---

## TASK-006 — JwtProperties Configuration Bean

**Component**: `com.docsync.config`  
**Dependencies**: TASK-001  
**Design Review RC**: FINDING-013 (startup validation of JWT secret length)

### Expected Files
- `src/main/java/com/docsync/config/JwtProperties.java`

### Implementation Details

```java
@ConfigurationProperties(prefix = "jwt")
@Validated
public class JwtProperties {
    @NotBlank private String secret;
    private long accessTokenExpiryMs;
    private long refreshTokenExpiryMs;
    private long rememberMeExpiryMs;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "jwt.secret must be at least 32 characters (256 bits). " +
                "Current length: " + (secret == null ? 0 : secret.length()));
        }
    }
    // getters, setters (not a record — Spring ConfigurationProperties requires setters)
}
```

Enable via `@EnableConfigurationProperties(JwtProperties.class)` in `SecurityConfig` or `LoginApiApplication`.

### Test Requirements

**`JwtPropertiesTest.java`**:
- `validateSecret_throwsOnShortSecret()` — assert `IllegalStateException` when secret < 32 chars.
- `validateSecret_passesOnValidSecret()` — no exception when secret is 32+ chars.

### Acceptance Criteria
- [ ] Application fails to start if `JWT_SECRET` is shorter than 32 characters.
- [ ] `@PostConstruct` validation in place.

---

## TASK-007 — JwtService

**Component**: `com.docsync.service`  
**Dependencies**: TASK-006, TASK-003

### Expected Files
- `src/main/java/com/docsync/service/JwtService.java`

### Implementation Details

Uses `io.jsonwebtoken:jjwt-api` 0.12.6. Key is derived from `JwtProperties.secret` using `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))`.

**Public methods** (all must have Javadoc):

```java
public String generateAccessToken(User user)
public String generateRefreshToken(User user)
public String generateRefreshToken(User user, boolean rememberMe)
public String extractUsername(String token)         // extracts "sub" claim
public boolean isTokenValid(String token, UserDetails userDetails)
public boolean isTokenExpired(String token)
public Date extractExpiration(String token)
private Claims extractAllClaims(String token)
```

**JWT Claims structure** (ADR-007):
```json
{
  "sub": "user@example.com",
  "userId": "<UUID>",
  "roles": ["ROLE_USER"],
  "iat": <now>,
  "exp": <now + accessTokenExpiryMs>
}
```

### Test Requirements

**`JwtServiceTest.java`** (JUnit 5, no Spring context):
- `generateAccessToken_returnsValidJwt()`
- `generateRefreshToken_usesDefaultExpiry()`
- `generateRefreshToken_withRememberMe_usesExtendedExpiry()`
- `extractUsername_returnsEmailFromSub()`
- `isTokenValid_returnsTrueForValidToken()`
- `isTokenValid_returnsFalseForExpiredToken()`
- `isTokenValid_returnsFalseForWrongUser()`
- `isTokenExpired_returnsTrueAfterExpiry()`

### Acceptance Criteria
- [ ] All public methods have Javadoc.
- [ ] HMAC-SHA256 signing only.
- [ ] `rememberMe=true` produces a token with `rememberMeExpiryMs` lifetime.
- [ ] Expired token returns `false` from `isTokenValid`.

---

## TASK-008 — LoginAttemptService

**Component**: `com.docsync.service`  
**Dependencies**: TASK-001 (Caffeine on classpath)

### Expected Files
- `src/main/java/com/docsync/service/LoginAttemptService.java`

### Implementation Details

```java
@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private final Cache<String, AtomicInteger> attemptsCache;
    private final MeterRegistry meterRegistry;

    public LoginAttemptService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();
    }

    public void recordFailure(String email) { ... }   // increments counter; emits metric on lock
    public void resetAttempts(String email) { ... }   // invalidates cache entry
    public boolean isLocked(String email) { ... }     // returns count >= MAX_ATTEMPTS
}
```

**FINDING-002 resolution**: `recordFailure(email)` must be called for BOTH "email not found" and "password mismatch" branches in `AuthService`. Document this in Javadoc.

**Caffeine expiry policy** (FINDING-018): `expireAfterWrite(15, MINUTES)` — counter expires 15 minutes after the last `recordFailure` write. Once locked (count ≥ 5), no further writes occur (login is rejected before BCrypt), so the entry naturally expires after 15 minutes. Document this behavior in the Javadoc.

### Test Requirements

**`LoginAttemptServiceTest.java`**:
- `recordFailure_incrementsCounter()`
- `isLocked_returnsFalseBeforeFiveFailures()`
- `isLocked_returnsTrueAfterFiveFailures()`
- `resetAttempts_clearsCounter()`
- `isLocked_returnsFalseAfterReset()`

### Acceptance Criteria
- [ ] Lock triggers at exactly 5 failures.
- [ ] `resetAttempts` removes the entry.
- [ ] Caffeine `expireAfterWrite(15, MINUTES)` configured.
- [ ] Custom counter metrics emitted via `MeterRegistry`.

---

## TASK-009 — TokenBlacklistService

**Component**: `com.docsync.service`  
**Dependencies**: TASK-001 (Caffeine on classpath)

### Expected Files
- `src/main/java/com/docsync/service/TokenBlacklistService.java`

### Implementation Details

```java
@Service
public class TokenBlacklistService {
    private final Cache<String, Boolean> blacklistCache;

    public TokenBlacklistService() {
        this.blacklistCache = Caffeine.newBuilder()
            .expireAfter(new TokenExpiry())   // custom Expiry<K,V> based on token's exp claim
            .build();
    }

    public void blacklist(String token, Date tokenExpiry) { ... }
    public boolean isBlacklisted(String token) { ... }
}
```

The custom `Expiry<String, Boolean>` implementation calculates TTL as `tokenExpiry.getTime() - System.currentTimeMillis()` nanoseconds, ensuring entries auto-evict when the token would have expired anyway (ADR-002).

### Test Requirements

**`TokenBlacklistServiceTest.java`**:
- `blacklist_addsTokenToCache()`
- `isBlacklisted_returnsTrueForBlacklistedToken()`
- `isBlacklisted_returnsFalseForUnknownToken()`

### Acceptance Criteria
- [ ] Blacklisted token returns `true` from `isBlacklisted`.
- [ ] Non-blacklisted token returns `false`.
- [ ] Custom `Expiry` implementation sets TTL relative to token's own expiry.

---

## TASK-010 — UserDetailsServiceImpl

**Component**: `com.docsync.service`  
**Dependencies**: TASK-004  
**Design Review RC**: RC-002 (CRITICAL — resolves FINDING-007)

### Expected Files
- `src/main/java/com/docsync/service/UserDetailsServiceImpl.java`

### Implementation Details

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by email address for Spring Security authentication.
     * @throws UsernameNotFoundException if no user with the given email exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + username));
    }
}
```

Since `User` implements `UserDetails` (TASK-003), no adapter wrapping is needed.

### Test Requirements

**`UserDetailsServiceImplTest.java`**:
- `loadUserByUsername_returnsUserForValidEmail()`
- `loadUserByUsername_throwsUsernameNotFoundExceptionForUnknownEmail()`

### Acceptance Criteria
- [ ] `UserDetailsService` bean registered in Spring context.
- [ ] Returns the `User` entity (which implements `UserDetails`) directly.
- [ ] Throws `UsernameNotFoundException` for missing email.

---

## TASK-011 — SecurityConfig and IpRateLimiterFilter

**Component**: `com.docsync.config`  
**Dependencies**: TASK-008, TASK-009, TASK-010  
**Design Review RC**: RC-001 (CRITICAL — per-IP rate limiting), RC-003 (package namespace), RC-004 (Swagger prod), RC-005 (Actuator)

### Expected Files
- `src/main/java/com/docsync/config/SecurityConfig.java`
- `src/main/java/com/docsync/config/IpRateLimiterFilter.java`
- `src/main/java/com/docsync/config/RateLimiterConfig.java`

### Implementation Details

**`IpRateLimiterFilter.java`** (RC-001 — replaces `@RateLimiter` annotation approach):

```java
@Component
public class IpRateLimiterFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(IpRateLimiterFilter.class);
    private final LoadingCache<String, RateLimiter> rateLimiters;

    public IpRateLimiterFilter(RateLimiterConfig config) {
        this.rateLimiters = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build(ip -> config.createRateLimiterForIp(ip));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {
        String ip = resolveClientIp(req);
        RateLimiter limiter = rateLimiters.get(ip);
        if (!limiter.acquirePermission()) {
            res.setStatus(HttpServletResponse.SC_ACCEPTED);   // 429
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write("""
                {"success":false,"message":"Too many requests. Please try again later.",
                 "data":null,"errors":null}""");
            return;
        }
        chain.doFilter(req, res);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
```

Note: Return `429` not `202` — use `HttpServletResponse.SC_TOO_MANY_REQUESTS = 429`.

**`RateLimiterConfig.java`**:

```java
@Configuration
public class RateLimiterConfig {
    private final RateLimiterRegistry registry;

    public RateLimiterConfig(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    public RateLimiter createRateLimiterForIp(String ip) {
        return registry.rateLimiter("authRateLimiter-" + ip,
            RateLimiterConfig.custom()
                .limitForPeriod(5)
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .timeoutDuration(Duration.ZERO)
                .build());
    }
}
```

**`SecurityConfig.java`**:

```java
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final IpRateLimiterFilter ipRateLimiterFilter;

    // Constructor injection

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(ipRateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Test Requirements

**`IpRateLimiterFilterTest.java`** (unit test with MockHttpServletRequest):
- `filter_allowsRequestUnderLimit()`
- `filter_returns429WhenLimitExceeded()`
- `filter_extractsIpFromXForwardedFor()`
- `filter_fallsBackToRemoteAddrWhenNoXff()`

### Acceptance Criteria
- [ ] `IpRateLimiterFilter` uses `LoadingCache<String, RateLimiter>` keyed by client IP.
- [ ] Returns HTTP 429 with standard `ApiResponse` JSON when limit exceeded.
- [ ] `X-Forwarded-For` header is used if present; `getRemoteAddr()` otherwise.
- [ ] No `@RateLimiter` annotation on controller methods.
- [ ] `SecurityConfig` registers `DaoAuthenticationProvider` with `UserDetailsServiceImpl`.
- [ ] CSRF disabled; session policy is STATELESS.

---

## TASK-012 — JwtAuthenticationFilter

**Component**: `com.docsync.config`  
**Dependencies**: TASK-007, TASK-009, TASK-010

### Expected Files
- `src/main/java/com/docsync/config/JwtAuthenticationFilter.java`

### Implementation Details

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    // Constructor injection

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        String token = authHeader.substring(7);

        if (tokenBlacklistService.isBlacklisted(token)) {
            sendUnauthorized(res, "Invalid or revoked token");
            return;
        }

        String username = jwtService.extractUsername(token);
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(token, userDetails)) {
                var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        chain.doFilter(req, res);
    }
}
```

### Test Requirements

**`JwtAuthenticationFilterTest.java`** (RC-008 — FINDING-020):
- `validToken_setsSecurityContext()`
- `expiredToken_returns401()`
- `blacklistedToken_returns401()`
- `missingAuthHeader_passesThrough()`
- `invalidBearerFormat_passesThrough()`

### Acceptance Criteria
- [ ] Valid token populates `SecurityContextHolder`.
- [ ] Expired or blacklisted tokens produce HTTP 401.
- [ ] Missing `Authorization` header passes through for public endpoints.
- [ ] No business logic in this class.

---

## TASK-013 — RequestCorrelationFilter

**Component**: `com.docsync.config`  
**Dependencies**: TASK-001  
**Design Review RC**: FINDING-008 (component must be explicitly designed)

### Expected Files
- `src/main/java/com/docsync/config/RequestCorrelationFilter.java`

### Implementation Details

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {
        String requestId = req.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        res.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

Filter order: `HIGHEST_PRECEDENCE` so `requestId` is in MDC before any other filter logs.

### Test Requirements
- `RequestCorrelationFilterTest.java`:
  - `populatesMdcWithRequestIdHeader()`
  - `generatesRequestIdWhenHeaderAbsent()`
  - `clearesMdcAfterRequest()`

### Acceptance Criteria
- [ ] MDC is populated before any downstream filter runs.
- [ ] `X-Request-Id` echoed in response header.
- [ ] MDC is cleared in `finally` block (no MDC leak).

---

## TASK-014 — AuthService

**Component**: `com.docsync.service`  
**Dependencies**: TASK-004, TASK-007, TASK-008, TASK-009

### Expected Files
- `src/main/java/com/docsync/service/AuthService.java`

### Implementation Details

```java
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final BCryptPasswordEncoder passwordEncoder;

    // Constructor injection

    public RegisterResponse register(RegisterRequest request) { ... }
    public LoginResponse login(LoginRequest request) { ... }
    public void logout(String accessToken) { ... }
    public RefreshResponse refresh(String refreshToken) { ... }
}
```

**`register` flow**:
1. Validate `confirmPassword == password` → `InvalidCredentialsException` (400)
2. Validate `acceptTerms == true` → `IllegalArgumentException` (400)
3. `userRepository.existsByEmail(email)` → throw `DuplicateEmailException` (409)
4. `passwordEncoder.encode(password)` → save `User`
5. Return `RegisterResponse`

**`login` flow** (FINDING-002 fix):
1. `loginAttemptService.isLocked(email)` → throw `AccountLockedException` (403)
2. `userRepository.findByEmail(email)` → if not found: `loginAttemptService.recordFailure(email)`, throw `InvalidCredentialsException` (401) — **this fixes FINDING-002**
3. `passwordEncoder.matches(raw, hash)` → if mismatch: `loginAttemptService.recordFailure(email)`, throw `InvalidCredentialsException` (401)
4. `loginAttemptService.resetAttempts(email)`
5. Generate tokens, save `RefreshToken`, return `LoginResponse`

**`logout` flow**:
1. Extract token expiry from JWT claims
2. `tokenBlacklistService.blacklist(token, expiry)`
3. Load user from security context → `refreshTokenRepository.deleteByUser(user)`

**`refresh` flow**:
1. `jwtService.isTokenExpired(refreshToken)` → throw `TokenExpiredException` (401)
2. `refreshTokenRepository.findByToken(refreshToken)` → if not found: throw `InvalidTokenException` (401)
3. Generate new access token → return `RefreshResponse`

### Test Requirements

**`AuthServiceTest.java`** (Mockito — mock all dependencies):
- `register_success_returnsRegisterResponse()`
- `register_duplicateEmail_throwsDuplicateEmailException()`
- `register_passwordMismatch_throwsException()`
- `register_acceptTermsFalse_throwsException()`
- `login_success_returnsLoginResponse()`
- `login_unknownEmail_recordsFailureAndThrowsInvalidCredentials()`  ← FINDING-002
- `login_wrongPassword_recordsFailureAndThrowsInvalidCredentials()`
- `login_accountLocked_throwsAccountLockedException()`
- `login_fifthFailure_locksAccount()`
- `login_successAfterFailures_resetsAttempts()`
- `logout_blacklistsToken()`
- `logout_deletesRefreshTokens()`
- `refresh_success_returnsNewAccessToken()`
- `refresh_expiredRefreshToken_throwsTokenExpiredException()`
- `refresh_unknownRefreshToken_throwsInvalidTokenException()`

### Acceptance Criteria
- [ ] All public methods have Javadoc.
- [ ] `recordFailure` called for BOTH email-not-found AND password-mismatch paths.
- [ ] `resetAttempts` called only on successful login.
- [ ] No password values in any log statement.
- [ ] All 14 test cases pass.

---

## TASK-015 — Models and DTOs

**Component**: `com.docsync.model`  
**Dependencies**: TASK-001

### Expected Files
- `src/main/java/com/docsync/model/request/RegisterRequest.java`
- `src/main/java/com/docsync/model/request/LoginRequest.java`
- `src/main/java/com/docsync/model/request/RefreshTokenRequest.java`
- `src/main/java/com/docsync/model/response/RegisterResponse.java`
- `src/main/java/com/docsync/model/response/LoginResponse.java`
- `src/main/java/com/docsync/model/response/RefreshResponse.java`
- `src/main/java/com/docsync/model/response/ApiResponse.java`

### Implementation Details

All are Java 17 `record` types with Jakarta Bean Validation annotations on request records.

```java
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
                       message = "Password must be at least 8 characters and contain uppercase, "
                               + "lowercase, digit, and special character")
    String password,
    @NotBlank String confirmPassword,
    @AssertTrue(message = "You must accept terms") boolean acceptTerms
) {}

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    boolean rememberMe
) {}

public record RefreshTokenRequest(@NotBlank String refreshToken) {}

public record RegisterResponse(UUID userId, String email, Instant createdAt) {}
public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            int expiresIn, UUID userId, String email) {}
public record RefreshResponse(String accessToken, int expiresIn) {}

public record ApiResponse<T>(boolean success, String message, T data, List<String> errors) {
    public static <T> ApiResponse<T> success(String message, T data) { ... }
    public static <T> ApiResponse<T> failure(String message, List<String> errors) { ... }
}
```

### Test Requirements
- No dedicated unit tests for records (tested via controller tests).

### Acceptance Criteria
- [ ] All request records use Jakarta Bean Validation annotations.
- [ ] `ApiResponse` is generic with static factory methods.
- [ ] All are `record` types (immutable).

---

## TASK-016 — AuthController and GlobalExceptionHandler

**Component**: `com.docsync.controller`, `com.docsync.exception`  
**Dependencies**: TASK-014, TASK-015

### Expected Files
- `src/main/java/com/docsync/controller/AuthController.java`
- `src/main/java/com/docsync/exception/GlobalExceptionHandler.java`

### Implementation Details

**`AuthController.java`**:

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) { ... }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) { ... }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) { ... }
}
```

**`GlobalExceptionHandler.java`** (complete mapping per architecture section 11):

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicate(DuplicateEmailException e) { ... }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException e) { ... }

    @ExceptionHandler(AccountLockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleLocked(AccountLockedException e) { ... }

    @ExceptionHandler({InvalidTokenException.class, TokenExpiredException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidToken(RuntimeException e) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) { ... }

    @ExceptionHandler(RequestNotPermitted.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiResponse<Void> handleRateLimit(RequestNotPermitted e) { ... }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleDataAccess(DataAccessException e) { ... }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnexpected(Exception e) { ... }

    // Also add DisabledException → HTTP 403 (FINDING-010)
    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleDisabled(DisabledException e) { ... }
}
```

### Test Requirements

**`AuthControllerTest.java`** (`@WebMvcTest(AuthController.class)`):
- `register_validRequest_returns201()`
- `register_invalidEmail_returns400()`
- `register_weakPassword_returns400()`
- `register_duplicateEmail_returns409()`
- `login_validCredentials_returns200WithTokens()`
- `login_invalidCredentials_returns401()`
- `login_lockedAccount_returns403()`
- `logout_validToken_returns200()`
- `logout_invalidToken_returns401()`
- `refresh_validRefreshToken_returns200()`
- `refresh_expiredToken_returns401()`

**`GlobalExceptionHandlerTest.java`** (RC-008 — FINDING-019, `@WebMvcTest`):
- One test per exception type in the handler (9 tests minimum).
- Every row in section 11 of architecture must be covered.

### Acceptance Criteria
- [ ] All public methods have Javadoc.
- [ ] `register` returns HTTP 201.
- [ ] All exception → HTTP status mappings tested in `GlobalExceptionHandlerTest`.
- [ ] `DisabledException` → HTTP 403 handled.
- [ ] Passwords never appear in response bodies.

---

## TASK-017 — RefreshTokenCleanupTask

**Component**: `com.docsync.service` (scheduler)  
**Dependencies**: TASK-004  
**Design Review RC**: FINDING-016 (cleanup task must be explicitly designed)

### Expected Files
- `src/main/java/com/docsync/service/RefreshTokenCleanupTask.java`

### Implementation Details

```java
@Component
public class RefreshTokenCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupTask.class);
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupTask(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Deletes all refresh tokens that have expired. Runs hourly.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        log.info("Refresh token cleanup: deleted {} expired tokens", deleted);
    }
}
```

Enable scheduling via `@EnableScheduling` on `LoginApiApplication`.

### Test Requirements

**`RefreshTokenCleanupTaskTest.java`**:
- `deleteExpiredTokens_callsRepositoryWithCurrentTime()`
- `deleteExpiredTokens_logsDeletedCount()`

### Acceptance Criteria
- [ ] `@Scheduled(cron = "0 0 * * * *")` — hourly execution.
- [ ] `@Transactional` on the method.
- [ ] Logs the count of deleted rows.
- [ ] `@EnableScheduling` present on application class.

---

## TASK-018 — Unit Test Suite

**Component**: All service and controller layers  
**Dependencies**: TASK-007 through TASK-017

### Expected Files

All test classes mirror the `src/main/java` structure under `src/test/java`:

```
src/test/java/com/docsync/
├── config/
│   ├── JwtPropertiesTest.java
│   ├── IpRateLimiterFilterTest.java
│   ├── JwtAuthenticationFilterTest.java     ← RC-008 (FINDING-020)
│   └── RequestCorrelationFilterTest.java
├── service/
│   ├── AuthServiceTest.java
│   ├── JwtServiceTest.java
│   ├── LoginAttemptServiceTest.java
│   ├── TokenBlacklistServiceTest.java
│   ├── UserDetailsServiceImplTest.java
│   └── RefreshTokenCleanupTaskTest.java
├── controller/
│   └── AuthControllerTest.java
└── exception/
    └── GlobalExceptionHandlerTest.java      ← RC-008 (FINDING-019)
```

### Test Requirements
- JUnit 5 for all tests.
- Mockito for all service-layer mocks.
- `@WebMvcTest` for controller and exception handler tests.
- No Spring context for pure service unit tests.
- Coverage must reach **≥ 80%** (enforced by JaCoCo `mvn verify`).

### Acceptance Criteria
- [ ] `mvn test` exits 0 with all tests passing.
- [ ] `mvn test jacoco:report` shows ≥ 80% instruction coverage.
- [ ] `GlobalExceptionHandlerTest` covers all 9 exception mappings.
- [ ] `JwtAuthenticationFilterTest` covers all 5 filter scenarios.

---

## TASK-019 — Integration Tests

**Component**: Integration test suite  
**Dependencies**: TASK-018  
**Design Review RC**: FINDING-022 (Testcontainers for DB), FINDING-021 (rate limiter test strategy)

### Expected Files
- `src/test/java/com/docsync/integration/AuthIntegrationTest.java`
- `src/test/java/com/docsync/integration/AuthRateLimiterIntegrationTest.java`

### Implementation Details

**`AuthIntegrationTest.java`** (`@SpringBootTest`, `@Tag("integration")`, Testcontainers PostgreSQL 15):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
class AuthIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    // Full end-to-end tests: register → login → use token → logout → verify invalidation
}
```

**`AuthRateLimiterIntegrationTest.java`** (FINDING-021 — uses `test` profile with limit=1):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@ActiveProfiles("test")
@Testcontainers
class AuthRateLimiterIntegrationTest {
    // test profile sets limit-for-period: 1, limit-refresh-period: 10s
    // test: second request from same IP returns 429
}
```

**Scenarios for `AuthIntegrationTest`**:
- Full registration → login → token use → logout → rejected token cycle (AC-001, AC-004, AC-007)
- Duplicate email registration (AC-002)
- 5 failed logins → account lock (AC-006)
- Refresh token flow (AC-008, AC-009)
- Expired access token rejected

### Acceptance Criteria
- [ ] All integration tests use Testcontainers PostgreSQL 15.
- [ ] All tests annotated `@Tag("integration")`.
- [ ] Rate limiter tests use `@ActiveProfiles("test")` with limit-for-period=1.
- [ ] `mvn verify -P integration-tests` exits 0.

---

## TASK-020 — Final Verification

**Component**: Quality gates  
**Dependencies**: TASK-019

### Steps

1. **Compile**: `mvn compile` — zero errors.
2. **Unit tests + coverage**: `mvn test jacoco:report` — all pass, ≥ 80% coverage.
3. **Integration tests**: `mvn verify -P integration-tests` — all pass.
4. **Checkstyle**: `mvn checkstyle:check` — zero violations.
5. **SpotBugs**: `mvn spotbugs:check` — zero HIGH/MEDIUM bugs.
6. **Secret scan**: `detect-secrets scan --all-files` — no secrets detected.
7. **App smoke test**: Start with `mvn spring-boot:run`, verify `GET /actuator/health` returns `{"status":"UP"}`.

### Acceptance Criteria
- [ ] All six quality gates pass.
- [ ] `verification-report.md` written to `docs/login-api-requirements/verification-report.md`.
- [ ] No secrets committed.
- [ ] Definition of Done checklist in CLAUDE.md satisfied.

---

## Open Architecture Questions Requiring Human Approval

Before implementation begins, the architecture.md must be updated by the architecture agent to address RC-001 through RC-008 and the following open questions must be resolved by Suraj Salunkhe:

| ID | Question | Blocks |
|---|---|---|
| OQ-A | Is the Login API a feature module of DocSync (`com.docsync`) or a standalone service (different root package)? | All tasks — package naming throughout |
| OQ-B | Should failed login attempts against non-existent email addresses be counted toward the lockout? | TASK-014 (AuthService.login) |
| OQ-C | Is refresh token rotation required? | TASK-014 (AuthService.refresh) |
| OQ-D | Should `/actuator/metrics` and `/actuator/info` be restricted to a separate management port (8081) or authentication-gated on port 8080? | TASK-002 (application.yml) |

---

## Summary Table

| Task | Component | Depends On | RC / Finding |
|---|---|---|---|
| TASK-001 | Maven POM | — | — |
| TASK-002 | application.yml + Flyway DDL | TASK-001 | RC-004, RC-005, RC-006 |
| TASK-003 | JPA Entities | TASK-002 | RC-002 (User implements UserDetails) |
| TASK-004 | Repositories | TASK-003 | — |
| TASK-005 | Custom Exceptions | TASK-001 | — |
| TASK-006 | JwtProperties | TASK-001 | FINDING-013 (secret validation) |
| TASK-007 | JwtService | TASK-006, TASK-003 | — |
| TASK-008 | LoginAttemptService | TASK-001 | FINDING-002, FINDING-018 |
| TASK-009 | TokenBlacklistService | TASK-001 | — |
| TASK-010 | UserDetailsServiceImpl | TASK-004 | RC-002 (CRITICAL) |
| TASK-011 | SecurityConfig + IpRateLimiterFilter | TASK-008–010 | RC-001 (CRITICAL), RC-003, RC-004 |
| TASK-012 | JwtAuthenticationFilter | TASK-007, TASK-009, TASK-010 | — |
| TASK-013 | RequestCorrelationFilter | TASK-001 | FINDING-008 |
| TASK-014 | AuthService | TASK-004, TASK-007–009 | FINDING-002 |
| TASK-015 | Models and DTOs | TASK-001 | — |
| TASK-016 | AuthController + GlobalExceptionHandler | TASK-014, TASK-015 | RC-008 (FINDING-019, FINDING-010) |
| TASK-017 | RefreshTokenCleanupTask | TASK-004 | FINDING-016 |
| TASK-018 | Unit Test Suite | TASK-007–017 | RC-008 (FINDING-019, FINDING-020) |
| TASK-019 | Integration Tests | TASK-018 | FINDING-021, FINDING-022 |
| TASK-020 | Final Verification | TASK-019 | RC-006, Definition of Done |

---

**Status**: DONE

Per CLAUDE.md, this implementation plan requires explicit approval from Suraj Salunkhe before any implementation task begins. Additionally, `architecture.md` must be updated to resolve RC-001 through RC-008 and approved before TASK-011 (IpRateLimiterFilter / SecurityConfig) and TASK-010 (UserDetailsServiceImpl) can be implemented.
