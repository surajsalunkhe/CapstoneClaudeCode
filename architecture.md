# Architecture: Login API

**Status**: PENDING HUMAN APPROVAL
**Version**: 1.0
**Date**: 2026-08-15
**Author**: Architecture Agent
**Requirements Source**: `docs/login-api-requirements/requirements.md` (APPROVED)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Alternatives](#2-architecture-alternatives)
3. [Recommended Architecture](#3-recommended-architecture)
4. [Component Design](#4-component-design)
5. [Package Structure](#5-package-structure)
6. [REST API Design](#6-rest-api-design)
7. [Data Model](#7-data-model)
8. [Data Flow](#8-data-flow)
9. [Technology Stack](#9-technology-stack)
10. [Security Boundaries](#10-security-boundaries)
11. [Error Handling Strategy](#11-error-handling-strategy)
12. [Observability](#12-observability)
13. [Scalability](#13-scalability)
14. [Deployment](#14-deployment)
15. [Architecture Decision Records](#15-architecture-decision-records)
16. [Risks and Mitigations](#16-risks-and-mitigations)

---

## 1. Overview

The Login API provides a self-contained, stateless authentication service implementing user registration, credential-based login (JWT-issued), token refresh, and logout with server-side token revocation. It is the entry point for all protected features in the application.

The service is built as a Spring Boot 3.x monolith with a layered architecture. It persists users and refresh tokens to PostgreSQL. Access tokens are short-lived JWTs (30 minutes). Token revocation on logout is tracked in an in-memory Caffeine cache (see ADR-002). Rate limiting is enforced per IP via Resilience4j before any business logic executes.

### Guiding Principles

- Stateless request handling for access tokens; minimal server-side state limited to refresh tokens and the revocation cache.
- Defense in depth: validation at every layer boundary (HTTP, Controller, Service, Repository).
- Fail-safe defaults: locked accounts, expired tokens, and invalid signatures all return the same response shape.
- No secret leakage: passwords never appear in responses or logs; JWT signing keys come exclusively from environment variables.

---

## 2. Architecture Alternatives

### Option A: Classic Layered Monolith (Controller, Service, Repository)

```
HTTP Client
    |
    v
AuthController          <- validates input, delegates to service
    |
    v
AuthService / JwtService / LoginAttemptService
    |
    v
UserRepository / RefreshTokenRepository
    |
    v
PostgreSQL
```

Pros:
- Lowest complexity for a single bounded context (authentication).
- Spring Boot auto-configuration works out of the box for all needed integrations.
- Easiest to test: each layer is independently unit-testable.
- No inter-service network calls; no service mesh required.

Cons:
- Less explicit about domain boundaries.
- All concerns live in one deployable unit; future extraction to a microservice requires refactoring.

### Option B: Hexagonal Architecture (Ports and Adapters)

```
HTTP Adapter (Controller)
    |
    v
Application Core (Use Cases / Domain)
    |
    v
Persistence Adapter (JPA) / Token Store Adapter (Cache)
```

Pros:
- Explicit separation of domain logic from infrastructure.
- Easier to swap persistence adapters (e.g., replace Caffeine with Redis) without touching domain code.
- Good long-term maintainability.

Cons:
- Higher upfront complexity and boilerplate for a single-bounded-context service.
- Overkill given that the only adapters are JPA and an in-memory cache.

### Option C: CQRS with Separate Read/Write Paths

Pros:
- Read (token validation) and write (registration, login) paths scale independently.

Cons:
- Massively over-engineered for this scope. Authentication is a low-read, low-write workload. The NFR latency targets (2 s / 3 s p95) are easily met with a simple layered design. CQRS introduces event bus complexity with no return.

---

## 3. Recommended Architecture

**Option A: Classic Layered Monolith** is recommended.

**Justification**

- The problem domain is a single bounded context (authentication) with four operations. There is no cross-domain communication to isolate.
- NFR-002 performance targets (under 2 s login, under 3 s registration at p95) are well within reach of a synchronous layered service backed by HikariCP.
- NFR-003 reliability (99.9% uptime) is achieved through connection pooling, graceful error handling, and a simple health endpoint — not topology complexity.
- All team constraints (Java 17, Spring Boot 3.x, Maven, Spring Security, JPA) are first-class citizens in this pattern.
- The design can be extracted to a microservice later with minimal refactoring because each layer has a single responsibility and is independently testable.

---

## 4. Component Design

Each component has exactly one responsibility. Constructor injection is mandatory (field @Autowired is forbidden per CLAUDE.md).

### 4.1 AuthController

**Package**: `com.docsync.controller`
**Responsibility**: Accept HTTP requests, delegate to `AuthService`, return standardized `ApiResponse<T>` envelopes. No business logic.

| Method | Path | Handler |
|---|---|---|
| POST | `/api/v1/auth/register` | `register(RegisterRequest)` |
| POST | `/api/v1/auth/login` | `login(LoginRequest)` |
| POST | `/api/v1/auth/logout` | `logout(HttpServletRequest)` |
| POST | `/api/v1/auth/refresh` | `refresh(RefreshTokenRequest)` |

### 4.2 AuthService

**Package**: `com.docsync.service`
**Responsibility**: Orchestrate registration and authentication flows. Coordinate between `UserRepository`, `JwtService`, `LoginAttemptService`, and `RefreshTokenRepository`. Enforce password hashing and business rules (duplicate email check, lockout check).

Key public methods:
- `RegisterResponse register(RegisterRequest request)`
- `LoginResponse login(LoginRequest request)`
- `void logout(String accessToken)`
- `RefreshResponse refresh(String refreshToken)`

### 4.3 JwtService

**Package**: `com.docsync.service`
**Responsibility**: Generate, sign, parse, and validate JWTs. Emit and verify HMAC-SHA256 signatures using a key sourced from `JwtProperties`. No database calls.

Key public methods:
- `String generateAccessToken(User user)`
- `String generateRefreshToken(User user)`
- `String generateRefreshToken(User user, boolean rememberMe)`
- `Claims extractAllClaims(String token)`
- `boolean isTokenValid(String token, UserDetails userDetails)`
- `boolean isTokenExpired(String token)`
- `String extractUsername(String token)`

### 4.4 LoginAttemptService

**Package**: `com.docsync.service`
**Responsibility**: Track failed login attempts per email using Caffeine cache. Enforce the 5-attempt lockout with 15-minute automatic expiry. Reset counter on successful login (see ADR-006).

Key public methods:
- `void recordFailure(String email)`
- `void resetAttempts(String email)`
- `boolean isLocked(String email)`

### 4.5 TokenBlacklistService

**Package**: `com.docsync.service`
**Responsibility**: Maintain a set of invalidated access tokens in a Caffeine cache. Cache entries expire at the token's own expiry time to avoid unbounded growth (see ADR-002).

Key public methods:
- `void blacklist(String token, Date tokenExpiry)`
- `boolean isBlacklisted(String token)`

### 4.6 UserRepository

**Package**: `com.docsync.repository`
**Responsibility**: JPA repository for `User` entities. Provides `findByEmail`, `existsByEmail`, `save`.

### 4.7 RefreshTokenRepository

**Package**: `com.docsync.repository`
**Responsibility**: JPA repository for `RefreshToken` entities. Provides `findByToken`, `deleteByUser`, `save`. Supports server-side revocation on logout.

### 4.8 JwtAuthenticationFilter

**Package**: `com.docsync.config`
**Responsibility**: Spring Security `OncePerRequestFilter`. Extracts the Bearer token from the `Authorization` header, validates it against `JwtService` and `TokenBlacklistService`, and sets the `SecurityContext`. Passes unauthenticated requests through for public endpoints.

### 4.9 SecurityConfig

**Package**: `com.docsync.config`
**Responsibility**: Configures the Spring Security filter chain. Defines which endpoints are public (`/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`). Registers `JwtAuthenticationFilter`. Disables CSRF (stateless JWT flow, per NFR-001.3). Configures `BCryptPasswordEncoder` bean with cost factor 10.

### 4.10 RateLimiterConfig

**Package**: `com.docsync.config`
**Responsibility**: Defines a Resilience4j `RateLimiter` bean named `authRateLimiter` (5 requests per 60 seconds per IP). Applied via `@RateLimiter` annotation on `AuthController` methods.

### 4.11 GlobalExceptionHandler

**Package**: `com.docsync.exception`
**Responsibility**: `@RestControllerAdvice` that maps all application exceptions and Spring framework exceptions to the standard `ApiResponse<Void>` envelope. One handler method per exception type; no catch-all swallowing.

| Exception | HTTP Status |
|---|---|
| `DuplicateEmailException` | 409 |
| `InvalidCredentialsException` | 401 |
| `AccountLockedException` | 403 |
| `InvalidTokenException` | 401 |
| `TokenExpiredException` | 401 |
| `MethodArgumentNotValidException` | 400 |
| `RequestNotPermitted` (Resilience4j) | 429 |
| `DataAccessException` | 503 |

### 4.12 Models and DTOs

**Package**: `com.docsync.model`

All request and response DTOs are Java 17 `record` types.

**Entities** (JPA, mutable):
- `User` — id (UUID), email, passwordHash, createdAt, updatedAt, enabled
- `RefreshToken` — id (UUID), token, user (FK), expiresAt, createdAt

**Request records**:
- `RegisterRequest` — email, password, confirmPassword, acceptTerms
- `LoginRequest` — email, password, rememberMe
- `RefreshTokenRequest` — refreshToken

**Response records**:
- `RegisterResponse` — userId, email, createdAt
- `LoginResponse` — accessToken, refreshToken, tokenType, expiresIn, userId, email
- `RefreshResponse` — accessToken, expiresIn
- `ApiResponse<T>` — success, message, data, errors

### 4.13 JwtProperties

**Package**: `com.docsync.config`
**Responsibility**: `@ConfigurationProperties(prefix = "jwt")` bean binding `jwt.secret`, `jwt.access-token-expiry-ms`, `jwt.refresh-token-expiry-ms`, `jwt.remember-me-expiry-ms` from `application.yml` (values sourced from environment variables).

---

## 5. Package Structure

```
src/main/java/com/docsync/
├── LoginApiApplication.java
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java
│   ├── JwtService.java
│   ├── LoginAttemptService.java
│   └── TokenBlacklistService.java
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   └── RefreshToken.java
│   ├── request/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── RefreshTokenRequest.java
│   └── response/
│       ├── RegisterResponse.java
│       ├── LoginResponse.java
│       ├── RefreshResponse.java
│       └── ApiResponse.java
├── config/
│   ├── SecurityConfig.java
│   ├── JwtAuthenticationFilter.java
│   ├── JwtProperties.java
│   └── RateLimiterConfig.java
└── exception/
    ├── DuplicateEmailException.java
    ├── InvalidCredentialsException.java
    ├── AccountLockedException.java
    ├── InvalidTokenException.java
    ├── TokenExpiredException.java
    └── GlobalExceptionHandler.java

src/test/java/com/docsync/
├── controller/
│   └── AuthControllerTest.java
├── service/
│   ├── AuthServiceTest.java
│   ├── JwtServiceTest.java
│   ├── LoginAttemptServiceTest.java
│   └── TokenBlacklistServiceTest.java
└── integration/
    └── AuthIntegrationTest.java
```

---

## 6. REST API Design

All responses use the envelope: `{ "success": boolean, "message": string, "data": object | null, "errors": array | null }`.

### POST /api/v1/auth/register

**Access**: Public
**Rate Limited**: Yes (5 req/min/IP)

Request body:
```json
{
  "email": "user@example.com",
  "password": "Passw0rd!",
  "confirmPassword": "Passw0rd!",
  "acceptTerms": true
}
```

Success — HTTP 201:
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "createdAt": "2026-08-15T10:00:00Z"
  },
  "errors": null
}
```

Error responses: HTTP 400 (validation), HTTP 409 (duplicate email), HTTP 429 (rate limit), HTTP 503 (DB unavailable).

### POST /api/v1/auth/login

**Access**: Public
**Rate Limited**: Yes (5 req/min/IP)

Request body:
```json
{
  "email": "user@example.com",
  "password": "Passw0rd!",
  "rememberMe": false
}
```

Success — HTTP 200:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<jwt>",
    "refreshToken": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com"
  },
  "errors": null
}
```

Error responses: HTTP 401 (invalid credentials, generic message "Invalid email or password"), HTTP 403 (account locked), HTTP 429 (rate limit), HTTP 503 (DB unavailable).

### POST /api/v1/auth/logout

**Access**: Protected (requires `Authorization: Bearer {accessToken}`)
**Rate Limited**: Yes (5 req/min/IP)

Request: no body.

Success — HTTP 200:
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null,
  "errors": null
}
```

Error responses: HTTP 401 (missing or invalid token).

### POST /api/v1/auth/refresh

**Access**: Public (refresh token supplied in body)
**Rate Limited**: Yes (5 req/min/IP)

Request body:
```json
{
  "refreshToken": "<jwt>"
}
```

Success — HTTP 200:
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "<jwt>",
    "expiresIn": 1800
  },
  "errors": null
}
```

Error responses: HTTP 401 (expired or invalid refresh token).

### Validation Error Envelope (HTTP 400)

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "errors": [
    {
      "field": "password",
      "message": "Password must be at least 8 characters and contain uppercase, lowercase, digit, and special character"
    },
    {
      "field": "confirmPassword",
      "message": "Passwords do not match"
    }
  ]
}
```

---

## 7. Data Model

### users table

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
```

### refresh_tokens table

```sql
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

### login_attempts (Caffeine cache — not persisted)

- Key: `email (String)`
- Value: `AtomicInteger (failureCount)`
- Expiry: 15 minutes after last write (`expireAfterWrite`)

### token_blacklist (Caffeine cache — not persisted)

- Key: `accessToken (String)`
- Value: `Instant (tokenExpiry)`
- Expiry: token's own expiry time (Caffeine custom `Expiry<K,V>`)

---

## 8. Data Flow

### 8.1 Registration Flow

```
Client
  |  POST /api/v1/auth/register
  v
RateLimiterFilter  -- [429 if exceeded]
  v
JwtAuthenticationFilter  (passes through -- public endpoint)
  v
AuthController.register(@Valid RegisterRequest)
  |  Jakarta Bean Validation  -- [400 if invalid]
  v
AuthService.register(RegisterRequest)
  |  confirmPassword == password  -- [400 if mismatch]
  |  acceptTerms == true          -- [400 if false]
  v
UserRepository.existsByEmail(email)  -- [409 DuplicateEmailException if exists]
  v
BCryptPasswordEncoder.encode(password)  [cost=10]
  v
UserRepository.save(new User)
  v
HTTP 201  RegisterResponse
```

### 8.2 Login Flow

```
Client
  |  POST /api/v1/auth/login
  v
RateLimiterFilter
  v
JwtAuthenticationFilter  (passes through)
  v
AuthController.login(@Valid LoginRequest)
  v
AuthService.login(LoginRequest)
  v
LoginAttemptService.isLocked(email)  -- [403 AccountLockedException if locked]
  v
UserRepository.findByEmail(email)  -- [401 generic if not found]
  v
BCryptPasswordEncoder.matches(rawPassword, hash)
  |  on failure --> LoginAttemptService.recordFailure(email)  -- [403 on 5th]
  |  on success --> LoginAttemptService.resetAttempts(email)
  v
JwtService.generateAccessToken(user)
JwtService.generateRefreshToken(user, rememberMe)
  v
RefreshTokenRepository.save(RefreshToken)
  v
HTTP 200  LoginResponse
```

### 8.3 Logout Flow

```
Client
  |  POST /api/v1/auth/logout
  |  Authorization: Bearer {accessToken}
  v
RateLimiterFilter
  v
JwtAuthenticationFilter
  |  Extracts and validates accessToken
  |  TokenBlacklistService.isBlacklisted(token)  -- [401 if blacklisted]
  |  Sets SecurityContext
  v
AuthController.logout(HttpServletRequest)
  v
AuthService.logout(accessToken)
  v
TokenBlacklistService.blacklist(token, tokenExpiry)
  v
RefreshTokenRepository.deleteByUser(currentUser)
  v
HTTP 200  "Logged out successfully"
```

### 8.4 Token Refresh Flow

```
Client
  |  POST /api/v1/auth/refresh
  |  Body: { "refreshToken": "<jwt>" }
  v
RateLimiterFilter
  v
JwtAuthenticationFilter  (passes through)
  v
AuthController.refresh(@Valid RefreshTokenRequest)
  v
AuthService.refresh(refreshToken)
  v
JwtService.isTokenExpired(refreshToken)  -- [401 TokenExpiredException if expired]
  v
RefreshTokenRepository.findByToken(refreshToken)  -- [401 if not found]
  v
JwtService.generateAccessToken(user)
  v
HTTP 200  RefreshResponse
```

---

## 9. Technology Stack

| Concern | Technology | Version | Rationale |
|---|---|---|---|
| Language | Java | 17 LTS | Fixed by CLAUDE.md; enables records, sealed classes, text blocks |
| Framework | Spring Boot | 3.x | Fixed by CLAUDE.md |
| Build | Maven | 3.9+ | Fixed by CLAUDE.md |
| Security | Spring Security | 6.x (Boot 3 bundle) | Filter chain, BCrypt, SecurityContext |
| ORM | Spring Data JPA + Hibernate | 6.x (Boot 3 bundle) | Repository pattern, DDL generation |
| Database | PostgreSQL | 15+ | See ADR-003 |
| JDBC Pool | HikariCP | Boot 3 bundle | NFR-003.2; best-in-class connection pool |
| JWT | io.jsonwebtoken:jjwt | 0.12.6 | See ADR-004 |
| In-memory Cache | Caffeine | 3.x | Token blacklist and login attempt counter; see ADR-002 |
| Rate Limiting | Resilience4j | 2.x | NFR-001.4; native Spring Boot 3 actuator integration |
| Validation | Jakarta Bean Validation + Hibernate Validator | 3.x | NFR-001.6 |
| API Docs | Springdoc OpenAPI | 2.x | NFR-005.4; auto-generates OpenAPI 3.0 spec |
| Testing | JUnit 5 + Mockito + MockMvc | Boot 3 bundle | Fixed by CLAUDE.md |
| Coverage | JaCoCo Maven Plugin | 0.8.x | NFR-005.2; minimum 80% |
| Static Analysis | Checkstyle + SpotBugs | Latest stable | C-004 |

---

## 10. Security Boundaries

### 10.1 Public Endpoints (no token required)

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`
- `GET /actuator/health`

### 10.2 Protected Endpoints (valid, non-blacklisted access token required)

- `POST /api/v1/auth/logout`

### 10.3 Defense Layers (outer to inner)

1. Transport: HTTPS-only. TLS configuration externalized; no TLS material committed to source control.
2. Rate Limiting: Resilience4j — 5 req/min/IP on all `/api/v1/auth/**`. Returns HTTP 429.
3. Input Validation: Jakarta Bean Validation on all request bodies before any service call.
4. Authentication: JWT HMAC-SHA256 signature verification on every protected request via `JwtAuthenticationFilter`.
5. Token Blacklist: Caffeine cache checked on every authenticated request; blacklisted tokens return HTTP 401.
6. Password Hashing: BCrypt cost factor 10 (`new BCryptPasswordEncoder(10)` bean; no delegating encoder).
7. Account Lockout: 5 consecutive failures locks the account for 15 minutes (Caffeine TTL).
8. Secret Management: JWT secret and DB credentials sourced exclusively from environment variables.
9. CSRF: Disabled for the stateless JWT flow (NFR-001.3 exempts stateless JWT flows).
10. Response Sanitization: Passwords never returned in any response. Stack traces never returned to clients.

### 10.4 Secrets Inventory

| Secret | Environment Variable | Consumed By |
|---|---|---|
| JWT signing key | `JWT_SECRET` | `JwtProperties.secret` |
| DB username | `DB_USERNAME` | `application.yml` datasource |
| DB password | `DB_PASSWORD` | `application.yml` datasource |
| DB URL | `DB_URL` | `application.yml` datasource |

---

## 11. Error Handling Strategy

All errors are surfaced via `GlobalExceptionHandler` (`@RestControllerAdvice`). No exception is caught and swallowed silently. Each exception type has a dedicated handler method returning `ApiResponse<Void>` with the appropriate HTTP status.

| Scenario | Exception | HTTP | message |
|---|---|---|---|
| Duplicate email | `DuplicateEmailException` | 409 | "Email already registered" |
| Invalid credentials | `InvalidCredentialsException` | 401 | "Invalid email or password" |
| Account locked | `AccountLockedException` | 403 | "Account locked due to too many failed attempts. Try again in 15 minutes." |
| Token expired | `TokenExpiredException` | 401 | "Token has expired" |
| Token invalid or blacklisted | `InvalidTokenException` | 401 | "Invalid or revoked token" |
| Bean validation failure | `MethodArgumentNotValidException` | 400 | "Validation failed" + field errors array |
| Rate limit exceeded | `RequestNotPermitted` (Resilience4j) | 429 | "Too many requests. Please try again later." |
| DB unavailable | `DataAccessException` | 503 | "Service temporarily unavailable" |
| Unhandled exception | Catch-all (logged at ERROR) | 500 | "An unexpected error occurred" |

### Logging Policy

- WARN level: authentication failures, locked account attempts, rate limit hits.
- ERROR level: unexpected exceptions (with stack trace), DB failures.
- No password values, JWT token strings, or PII are written to logs.

---

## 12. Observability

### 12.1 Health Check

Spring Boot Actuator `GET /actuator/health`. Custom `HealthIndicator` for DB connectivity. Exposed without authentication.

### 12.2 Metrics (Micrometer via Spring Boot Actuator)

- `http.server.requests` — latency per endpoint (supports NFR-002 p95 monitoring).
- `resilience4j.ratelimiter.available.permissions` — rate limiter saturation.
- `login.attempts.failed` — custom counter in `LoginAttemptService` via `MeterRegistry`.
- `login.attempts.locked` — custom counter for account lockout events.
- `token.blacklist.size` — Caffeine cache size gauge.

### 12.3 Structured Logging

SLF4J + Logback. Log fields per line: `timestamp`, `level`, `traceId` (MDC), `spanId` (MDC), `class`, `message`. No raw exception messages exposed to clients.

### 12.4 Request Correlation

MDC populated with a per-request `X-Request-Id` header (generated if absent by a `OncePerRequestFilter`) for log correlation across components.

---

## 13. Scalability

- Horizontal scaling: The service is stateless with respect to access tokens. Multiple instances can run behind a load balancer. The only shared state is PostgreSQL (refresh tokens) and the in-memory caches.
- Cache limitation: The Caffeine-based token blacklist and login attempt counter are per-JVM. In a multi-instance deployment, a logged-out token on instance A is not blacklisted on instance B until its natural expiry (at most 30 minutes). See ADR-002 for the accepted trade-off and the Redis migration path.
- Connection pooling: HikariCP with configurable pool size (default 10, tunable via `spring.datasource.hikari.maximum-pool-size`).
- Rate limiting: Resilience4j rate limiter is per-JVM. In multi-instance deployments the effective limit is `5 * instanceCount` per IP per minute. Acceptable at current scale; Redis-backed distributed rate limiting is the defined migration path.

---

## 14. Deployment

### 14.1 Local Development

```bash
# Start PostgreSQL
docker run -d --name login-db \
  -e POSTGRES_DB=logindb \
  -e POSTGRES_USER=${DB_USERNAME} \
  -e POSTGRES_PASSWORD=${DB_PASSWORD} \
  -p 5432:5432 postgres:15

# Run the application
JWT_SECRET=<min-32-char-secret> \
DB_URL=jdbc:postgresql://localhost:5432/logindb \
DB_USERNAME=<user> \
DB_PASSWORD=<pass> \
mvn spring-boot:run
```

### 14.2 application.yml Structure

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
  hikari:
    maximum-pool-size: 10
    connection-timeout: 30000

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
  swagger-ui:
    path: /swagger-ui.html
```

### 14.3 CI Pipeline (GitHub Actions)

```
on: push, pull_request
jobs:
  build:
    steps:
      - mvn checkstyle:check
      - mvn spotbugs:check
      - mvn test                       # unit tests + JaCoCo
      - mvn verify -P integration-tests
      - detect-secrets scan --all-files
```

---

## 15. Architecture Decision Records

### ADR-001: rememberMe Token Lifetime Extension (resolves OQ-001)

**Decision**: When `rememberMe: true`, the refresh token expiry is extended from 7 days to 30 days (2 592 000 000 ms). The access token expiry remains 30 minutes regardless of `rememberMe`.

**Rationale**: Extending only the refresh token (not the access token) limits the blast radius of a compromised access token while still providing the long-lived session experience users expect from "remember me". 30 days is a widely accepted industry default (used by Google, GitHub). The access token's short lifetime is preserved as a security control.

**Consequences**: `JwtService.generateRefreshToken(user, rememberMe)` reads `jwt.remember-me-expiry-ms` vs `jwt.refresh-token-expiry-ms`. The `refresh_tokens.expires_at` column reflects whichever duration applies.

---

### ADR-002: Token Blacklist Implementation (resolves OQ-002)

**Decision**: Use an in-memory Caffeine cache for the access token blacklist. Each entry is keyed by the raw token string and expires at the token's own `exp` claim time, so the cache never retains stale entries longer than necessary.

**Rationale**: Redis adds an operational dependency (provisioning, monitoring, network latency) that is not justified for a single-instance deployment. The blacklist only needs to survive until the token naturally expires (at most 30 minutes). A process restart clears the cache, but all previously-issued access tokens are still self-expiring JWTs — the window of risk is at most 30 minutes per restarted instance.

**Migration Path to Redis**: Extract `TokenBlacklistService` to an interface. Add a `RedisTokenBlacklistService` implementation activated via a Spring profile (`@Profile("multi-instance")`). No other component changes are required.

---

### ADR-003: Database Engine (resolves OQ-003)

**Decision**: PostgreSQL 15+.

**Rationale**: PostgreSQL's `gen_random_uuid()` natively supports UUID primary key generation. Better standards compliance, richer JSON support, and more predictable locking behavior compared to MySQL. The PostgreSQL JDBC driver is actively maintained and integrates well with HikariCP and Hibernate 6. No licensing concerns for production use.

**Consequences**: DDL uses the `UUID` type, `gen_random_uuid()`, and `TIMESTAMPTZ`. Hibernate dialect `org.hibernate.dialect.PostgreSQLDialect` is auto-detected by Spring Boot 3 when the PostgreSQL driver is on the classpath.

---

### ADR-004: JWT Library (resolves OQ-004)

**Decision**: `io.jsonwebtoken:jjwt-api` + `io.jsonwebtoken:jjwt-impl` + `io.jsonwebtoken:jjwt-jackson`, version 0.12.6.

**Rationale**: JJWT 0.12.x is the current stable release with full Java 17 support and active maintenance. The split API/impl/jackson module structure means only the API module leaks to downstream code, keeping the implementation swappable. Supports HMAC-SHA256 (`HS256`) signing natively. JJWT is the de-facto standard in the Spring Boot ecosystem.

**Consequences**: `JwtService` depends only on `io.jsonwebtoken:jjwt-api`. The `jjwt-impl` and `jjwt-jackson` artifacts are declared with `runtime` scope in `pom.xml`.

---

### ADR-005: Email Verification (resolves OQ-005)

**Decision**: Email verification is out of scope for this iteration.

**Rationale**: Per Assumption A-003 in the approved requirements, email verification is explicitly deferred. Adding it now would expand scope beyond the approved requirements. It can be introduced as a follow-on story adding a `verified` boolean column to `users` and a `POST /api/v1/auth/verify-email` endpoint.

---

### ADR-006: Failed Login Attempt Counter Reset (resolves OQ-006)

**Decision**: The failed login attempt counter resets on successful login.

**Rationale**: The alternative (only resetting after the lockout period expires) would lock out a user who corrects their password after 4 failures, degrading legitimate user experience without meaningful security gain. Resetting on success is the standard behavior and aligns with how most authentication systems (including Spring Security's own event publisher pattern) behave.

**Consequences**: `AuthService.login()` calls `LoginAttemptService.resetAttempts(email)` immediately after a successful BCrypt match, before generating tokens.

---

### ADR-007: User Role Model (resolves OQ-007)

**Decision**: Single-role flat model for this iteration. All registered users receive `ROLE_USER`. The role is embedded as a `roles` claim (JSON array) in the JWT for forward compatibility.

**Rationale**: The requirements do not specify multiple roles or an admin flow. Embedding the role as an array (even with a single value) means that when `ROLE_ADMIN` is added in a future story, the JWT structure does not change — only the service logic that assigns roles changes.

**JWT Claims Structure**:
```json
{
  "sub": "user@example.com",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "roles": ["ROLE_USER"],
  "iat": 1723708800,
  "exp": 1723710600
}
```

**Consequences**: No `roles` table is needed in this iteration. `JwtService.generateAccessToken()` hard-codes `ROLE_USER` in the `roles` claim. When roles are introduced, a `user_roles` join table and `Role` entity are added; `JwtService` reads from the entity.

---

## 16. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| In-memory blacklist does not survive JVM restart | Medium | Medium | Accepted for single-instance. Access tokens expire in 30 min regardless. Redis migration path defined in ADR-002. |
| Blacklist not shared across instances in horizontal scale | Medium | Medium | Rate limiting and short token TTL reduce blast radius. Redis migration path documented. |
| BCrypt cost 10 adds ~100-200 ms per operation | Low | Low | p95 targets (2 s login, 3 s registration) absorb this easily. HikariCP eliminates connection overhead. |
| Login attempt counter not shared across instances | Medium | Low | Each instance independently enforces lockout. Attacker distributing across N instances gets N x 5 attempts before any single lock. Acceptable at current scale; Redis resolves it. |
| JWT secret rotation invalidates all live sessions | Low | Medium | Document rotation runbook. Future story: support multiple active signing keys (JWKS endpoint) for zero-downtime rotation. |
| PostgreSQL unavailability | Low | High | `DataAccessException` caught in `GlobalExceptionHandler` returns HTTP 503. HikariCP connection timeout is 30 s. Alert on health check failure. |
| Refresh token table grows unboundedly | Low | Medium | Add a scheduled `@Scheduled` cleanup job deleting `refresh_tokens` where `expires_at < NOW()`. Tracked as follow-on implementation task. |

---

## Appendix: Component Dependency Diagram

```
+--------------------------------------------------+
|               HTTP Client (HTTPS)                |
+------------------------+-------------------------+
                         |
                         v
+--------------------------------------------------+
|           Spring Security Filter Chain           |
|  +--------------------+  +--------------------+  |
|  | RateLimiterFilter  |  | JwtAuthentication  |  |
|  | (Resilience4j)     |->| Filter             |  |
|  | 5 req/min/IP->429  |  | validate+blacklist |  |
|  +--------------------+  +--------------------+  |
+------------------------+-------------------------+
                         |
                         v
+--------------------------------------------------+
|         AuthController (@RestController)         |
|   register | login | logout | refresh            |
|   @Valid on all request bodies                   |
+--------+-------------------+--------------------+
         |                   |
         v                   v
+--------+--------+  +-------+-----------------+
|  AuthService    |  |  GlobalExceptionHandler  |
|  register()     |  |  maps exceptions to      |
|  login()        |  |  ApiResponse<Void>       |
|  logout()       |  +-------------------------+
|  refresh()      |
+-+------+------+-+
  |      |      |
  v      v      v
+-------+ +----------------+ +--------------------+
|JwtSvc | |LoginAttemptSvc | |TokenBlacklistSvc   |
|gen    | |recordFailure() | |blacklist()         |
|valid  | |resetAttempts() | |isBlacklisted()     |
|parse  | |isLocked()      | |(Caffeine cache)    |
+-------+ |(Caffeine cache)| +--------------------+
          +----------------+
  |
  v
+--------------------------------------------------+
|           Spring Data JPA Repositories           |
|   UserRepository      RefreshTokenRepository     |
+------------------------+-------------------------+
                         | HikariCP
                         v
                  +--------------+
                  |  PostgreSQL  |
                  |  users       |
                  |  refresh_    |
                  |  tokens      |
                  +--------------+
```

---

**Status**: PENDING HUMAN APPROVAL

This document must be reviewed and explicitly approved by Suraj Salunkhe before any implementation planning begins. Per CLAUDE.md, no implementation activity may start until this architecture is approved.
