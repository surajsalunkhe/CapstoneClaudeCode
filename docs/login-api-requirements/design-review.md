# Design Review: Login API

**Status**: APPROVED WITH CHANGES REQUIRED
**Reviewer**: Design Review Agent (Principal Engineer)
**Date**: 2026-08-15
**Architecture Version Reviewed**: 1.0 (PENDING HUMAN APPROVAL)
**Requirements Reviewed**: `docs/login-api-requirements/requirements.md` (APPROVED)

---

## Review Summary

**Verdict: APPROVED WITH CHANGES REQUIRED**

The architecture is structurally sound for a Spring Boot layered monolith and correctly addresses the majority of functional and non-functional requirements. Seven ADRs resolve all open questions from requirements. However, two CRITICAL defects and six HIGH defects must be resolved before implementation can begin. The most severe finding is that the proposed rate-limiting mechanism — Resilience4j `@RateLimiter` on controller methods — does not provide per-IP enforcement and cannot satisfy NFR-001.4 as written. A second CRITICAL defect is the absence of a `UserDetailsService` component, which prevents the Spring Security filter chain from booting.

CRITICAL and HIGH findings require architecture.md updates and human approval before implementation planning proceeds.

---

## Findings

### Area 1: Requirements Alignment

**FINDING-001 | INFO**
All 21 functional requirements (FR-001 through FR-004) are addressed by at least one named component and data flow. All seven NFRs are traceable to architecture sections. All seven open questions from requirements are resolved by ADR-001 through ADR-007. Traceability is complete.

**FINDING-002 | MEDIUM**
FR-002.6 states the account is locked after 5 consecutive failures. The login flow in section 8.2 shows `recordFailure()` is only called on BCrypt mismatch, not on a non-existent email path. The data flow reads:
```
UserRepository.findByEmail(email) -- [401 generic if not found]
```
When the email is not found, `recordFailure()` is never called, so repeated attempts with a non-existent email are not counted toward the 5-attempt lockout. This is inconsistent with FR-002.6 which requires failure counting "per account" — but the implied intent is per email (not per existing account), because the requirement is an anti-enumeration control. The architecture must explicitly state whether failed attempts against non-existent email addresses are counted (recommend: yes, count them, keyed on the submitted email string regardless of existence).

Required change: Clarify and update section 8.2 to show `recordFailure(email)` is called for both "email not found" and "password mismatch" branches. Update ADR-006 to document this decision.

**FINDING-003 | MEDIUM**
FR-003.3 and FR-003.4 describe the token refresh path: "issue a new access token." The architecture's data flow (section 8.4) does not rotate the refresh token — the old refresh token remains valid in the database after issuing a new access token. Refresh token rotation (invalidate the old refresh token and issue a new one alongside the new access token) is an industry-standard defense against token replay attacks if a refresh token is stolen. The requirements do not explicitly require rotation, but the architecture should document the deliberate decision not to rotate, as this is a known security trade-off.

Required change: Add a decision in section 8.4 and/or a new ADR explicitly accepting the no-rotation stance or specifying rotation behavior.

**FINDING-004 | INFO**
The `expiresIn` field in `LoginResponse` and `RefreshResponse` is documented as returning `1800` (seconds). This aligns with FR-002.4 and FR-003.4 correctly. The access token lifetime (1 800 000 ms in `application.yml`) matches. No gap found.

---

### Area 2: Architecture Quality

**FINDING-005 | HIGH**
The architecture places all Login API components in `com.docsync.*` — the same root package used by the existing DocSync application per CLAUDE.md (`com.docsync.controller`, `com.docsync.service`, etc.). The CLAUDE.md project structure shows `DocSyncApplication.java` at `com.docsync`. If the Login API `AuthController` occupies `com.docsync.controller` alongside any existing DocSync controllers, Spring's component scan will pick up both. This creates a package namespace collision: either the Login API is a feature module of DocSync (acceptable, but must be explicit) or it is a separate application (in which case a different root package, e.g., `com.example.auth`, must be used). The architecture is ambiguous on this point and the package choice was made without justification.

Required change: The architecture must explicitly state whether the Login API is a feature module of the DocSync application (acceptable — but then integration with the existing DocSync application context must be addressed) or a standalone Spring Boot application (in which case a distinct root package must be declared and `LoginApiApplication.java` must differ from `DocSyncApplication.java`).

**FINDING-006 | CRITICAL**
The architecture specifies per-IP rate limiting (NFR-001.4: "max 5 requests per minute per IP") and implements it via `@RateLimiter(name = "authRateLimiter")` annotation on `AuthController` methods (section 4.10). This mechanism does NOT provide per-IP rate limiting. Resilience4j's `@RateLimiter` applies a single shared counter across all callers of that method — it is an application-global limiter, not a per-client limiter. With 5 concurrent legitimate users each making 1 request per minute, the global limit would be exhausted. Conversely, a single attacker making 5 requests gets through; subsequent legitimate users are blocked.

To achieve per-IP rate limiting, the implementation requires a filter-level approach (a `OncePerRequestFilter` subclass) that maintains a `LoadingCache<String, RateLimiter>` keyed by client IP address (extracted from `X-Forwarded-For` or `HttpServletRequest.getRemoteAddr()`), with one Resilience4j `RateLimiter` instance per IP.

The current design cannot satisfy NFR-001.4 or AC-010 as written. This blocks implementation.

Required change: Replace the `@RateLimiter` annotation approach with a dedicated `IpRateLimiterFilter extends OncePerRequestFilter` component. Add it to section 4 as a named component. Update section 8 data flows. Update `RateLimiterConfig` to provision a `LoadingCache<String, RateLimiter>` factory. Update the component diagram.

**FINDING-007 | CRITICAL**
Spring Security 6 requires a `UserDetailsService` bean (or equivalent `ReactiveUserDetailsService`) to load user details during authentication and for the `JwtAuthenticationFilter` to verify token subjects against live user state. The architecture defines no `UserDetailsService` implementation. Without it:
- The Spring Security filter chain will fail to configure during application startup if `HttpSecurity` is configured with `authenticationProvider(DaoAuthenticationProvider)`.
- The `JwtAuthenticationFilter` has no mechanism to load a `UserDetails` object to set in the `SecurityContext` after token validation.
- The `isTokenValid(String token, UserDetails userDetails)` method in `JwtService` (section 4.3) requires a `UserDetails` argument that must come from somewhere.

Required change: Add a `UserDetailsServiceImpl` component to section 4 (in `com.docsync.service` or `com.docsync.security`) that implements `UserDetailsService`, loads `User` by email from `UserRepository`, and maps to Spring Security's `UserDetails`. Register it in `SecurityConfig` via `authenticationProvider`. Add its test class to section 5.

**FINDING-008 | MEDIUM**
The `X-Request-Id` correlation ID filter described in section 12.4 is not listed as a named component in section 4 and has no package placement, class name, or responsibility definition. It is referenced only in the observability section. As a `OncePerRequestFilter` subclass, it needs explicit design.

Required change: Add `RequestCorrelationFilter` as a component in section 4 with package, responsibility, and position in the filter chain relative to `JwtAuthenticationFilter`.

**FINDING-009 | MEDIUM**
The logout flow (section 8.3) calls `RefreshTokenRepository.deleteByUser(currentUser)`, which deletes ALL refresh tokens for the authenticated user. This means logging out from one device invalidates all concurrent sessions on all other devices. The requirements do not address multi-device session management, but this behavior (full session invalidation on any logout) is a significant UX decision that is not documented and not traceable to any requirement or ADR.

Required change: Add an ADR or explicit decision note documenting that logout invalidates all sessions for the user and that per-device session management is out of scope for this iteration.

**FINDING-010 | LOW**
The `User` entity has an `enabled` field (schema: `DEFAULT TRUE`), but the architecture never specifies when this field is set to `false`, whether it feeds into Spring Security's `UserDetails.isEnabled()`, or what HTTP response a disabled-but-not-locked user receives. If `UserDetails.isEnabled()` returns `false`, Spring Security raises `DisabledException`, which is not listed in the `GlobalExceptionHandler` exception map.

Required change: Either define the lifecycle of `enabled` and add `DisabledException` → HTTP 403 to the exception handler, or remove `enabled` from the schema if it serves no purpose in this iteration.

---

### Area 3: Security

**FINDING-011 | HIGH**
The Swagger UI and API docs endpoints (`/v3/api-docs/**`, `/swagger-ui/**`) are listed as public endpoints with no profile restriction (section 10.1). In a production deployment, these endpoints expose a complete machine-readable map of every API route, request schema, and response structure. The architecture does not define a mechanism (e.g., `@Profile("!prod")` on the Springdoc bean, or a Spring Security rule restricting these paths in production) to disable or protect them in non-development environments.

Required change: Add a `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` configuration that defaults to `false` in production (`application-prod.yml`). Document the production profile strategy or restrict these endpoints via Spring Security in production.

**FINDING-012 | HIGH**
The actuator management configuration exposes `health`, `info`, and `metrics` endpoints (section 14.2). Section 10.1 lists only `GET /actuator/health` as a public endpoint. However, `/actuator/metrics` and `/actuator/info` are also enabled and their access control is not defined. By Spring Boot default, all actuator endpoints on the same port as the application are reachable without authentication. The `login.attempts.failed` and `token.blacklist.size` metrics would be exposed to any caller, leaking operational intelligence.

Required change: Either move actuator endpoints to a separate management port (`management.server.port`) or add Spring Security rules restricting `/actuator/metrics` and `/actuator/info` to authenticated users or a network-restricted range. Update section 10 security boundaries.

**FINDING-013 | MEDIUM**
The `JWT_SECRET` environment variable is bound via `@ConfigurationProperties` with no startup validation of minimum length or entropy. HMAC-SHA256 requires a minimum key size of 256 bits (32 bytes) for security. A misconfigured deployment with a short or weak secret will start without error. If the secret is shorter than 32 bytes, JJWT 0.12.x will silently pad or reject it at token generation time (behavior varies), leading to unexpected runtime failures or weakened security.

Required change: Add a `@PostConstruct` validation in `JwtProperties` (or a `@Bean` validation step in `SecurityConfig`) that asserts `jwt.secret` is at least 32 characters (256 bits) and throws a `IllegalStateException` at startup if the check fails. Document this in section 4.13.

**FINDING-014 | MEDIUM**
The JWT claims structure (ADR-007) does not include a `jti` (JWT ID) claim. The token blacklist in `TokenBlacklistService` uses the full raw JWT string as the cache key. A JWT string for an HMAC-SHA256 token is typically 200-400 bytes. Under high logout volume, the Caffeine cache will hold many large string keys. More critically, without a `jti`, there is no compact, canonical identifier for a token — making future integration with distributed token revocation (e.g., Redis) more expensive, as it must store full token strings rather than compact UUIDs.

Required change (MEDIUM, not blocking): Add a `jti` (UUID) claim to the JWT payload in ADR-007. Update `TokenBlacklistService` to key on `jti` rather than the full token string. Update `JwtService` to generate and extract `jti`.

**FINDING-015 | LOW**
The logging policy (section 11) prohibits writing JWT token strings to logs, but does not explicitly prohibit logging the `email` field. Email addresses are PII under most privacy regulations (GDPR, CCPA). Login failure logs (WARN level) likely include the email being attempted, which constitutes PII in logs. The architecture should state whether email logging is permitted, and if so, whether emails must be masked/hashed before logging.

Required change: Add a PII logging policy to section 11 specifying whether and how email addresses may appear in log output.

---

### Area 4: Reliability

**FINDING-016 | MEDIUM**
The `refresh_tokens` table will grow unboundedly. The architecture acknowledges this in section 16 (Risks) as a "follow-on implementation task" but provides no design: no `@EnableScheduling`, no cleanup interval, no transactional safety note, no index strategy supporting efficient delete-where-expired. Leaving this entirely undesigned means implementation will skip it.

Required change: Add a `RefreshTokenCleanupTask` component to section 4 (in `com.docsync.service` or a `com.docsync.scheduler` package) with a defined cron expression (e.g., `@Scheduled(cron = "0 0 * * * *")` — hourly), transactional boundary, and the query `DELETE FROM refresh_tokens WHERE expires_at < NOW()`. This is implementation-ready design, not a follow-on story.

**FINDING-017 | MEDIUM**
No circuit breaker is specified for database operations. The `connection-timeout: 30000` on HikariCP means each thread attempting a DB call during an outage can block for up to 30 seconds before failing. With a default Spring Boot thread pool (typically 200 Tomcat threads), a DB outage under moderate load will exhaust all server threads in under 30 seconds, making the service completely unresponsive even to endpoints that do not require DB access (e.g., health check on the same port). The architecture should specify a Resilience4j `CircuitBreaker` wrapping repository calls, or at minimum reduce the HikariCP `connection-timeout` to 3-5 seconds and ensure the health endpoint is served independently.

Required change: Add a Resilience4j `CircuitBreaker` instance for database operations to section 4.10 / `RateLimiterConfig` (or a dedicated `ResilienceConfig`), or document a lower `connection-timeout` (3000 ms) and acknowledge the thread-starvation risk explicitly in section 16.

**FINDING-018 | LOW**
The Caffeine cache for `LoginAttemptService` specifies `expireAfterWrite` for the 15-minute lockout. The architecture should verify that `recordFailure()` does not write a new entry when the account is already locked (HTTP 403 path returns before BCrypt check, so `recordFailure()` is not called — this appears correct from section 8.2). However, the exact Caffeine expiry policy should be spelled out: does the counter survive past 15 minutes if no successful login occurs? With `expireAfterWrite`, the entry expires 15 minutes after the last `recordFailure()` write. If the account is locked and no further writes occur (correct, per flow), the entry naturally expires after 15 minutes. Confirm this is the intended behavior and document it explicitly in section 4.4.

---

### Area 5: Testing

**FINDING-019 | HIGH**
The test structure in section 5 does not include a test class for `GlobalExceptionHandler`. Exception handler behavior is the last line of defense for the response contract — every exception-to-HTTP-status mapping in section 11 must be tested. An untested exception handler routinely drifts from its specification.

Required change: Add `GlobalExceptionHandlerTest` to the test structure in section 5. Specify that it tests every mapping row in the error handling table (section 11) using `MockMvc` with `@WebMvcTest`.

**FINDING-020 | HIGH**
No test class is listed for `JwtAuthenticationFilter` (section 4.8). The filter performs three security-critical operations: Bearer token extraction, JWT signature validation, and blacklist check. These must be unit tested independently of the full Spring context. A filter that silently fails open (passes all requests through) would not be caught by controller tests that mock the security layer.

Required change: Add `JwtAuthenticationFilterTest` to the test structure in section 5. Specify that it tests: valid token sets SecurityContext, expired token returns 401, blacklisted token returns 401, missing Authorization header passes through for public endpoints.

**FINDING-021 | MEDIUM**
No test strategy is defined for the Resilience4j rate limiter behavior. The `AuthIntegrationTest` is listed but its scope is undefined. Testing rate limiting requires either (a) resetting the rate limiter state between tests (not straightforward with Resilience4j), or (b) using a test profile with a very low limit (1 req/5s) to trigger the limit without requiring 5 rapid requests. Without a defined strategy, rate limiter integration tests will either be absent or flaky.

Required change: Add a test strategy note to section 5 specifying the rate limiter test approach: a `test` profile with `limit-for-period: 1` and `limit-refresh-period: 10s`, and an explicit `AuthRateLimiterIntegrationTest` annotated `@Tag("integration")`.

**FINDING-022 | MEDIUM**
No database strategy is defined for integration tests. `AuthIntegrationTest` presumably requires a PostgreSQL instance but the architecture does not specify Testcontainers, H2, or another isolation mechanism. Without Testcontainers or a dedicated test database, integration tests become environment-dependent and will fail in CI without a running PostgreSQL.

Required change: Add a statement to section 5 specifying that integration tests use Testcontainers (`org.testcontainers:postgresql`) to spin up an ephemeral PostgreSQL 15 instance per test run. Add `testcontainers` as a test dependency to the technology stack table (section 9).

---

### Area 6: Operational Readiness

**FINDING-023 | HIGH**
No database migration tooling is specified. The architecture uses `ddl-auto: validate` (correct for production) and provides DDL in section 7, but does not identify who executes the DDL and how. Without Flyway or Liquibase, the DDL in section 7 is documentation-only — it has no automated execution path in CI/CD. A deployment to a fresh environment would require manual DDL execution, and incremental schema changes have no version history.

Required change: Add Flyway (or Liquibase) to the technology stack (section 9) and deployment section (14). Specify that the DDL from section 7 becomes `src/main/resources/db/migration/V1__create_auth_tables.sql`. Set `spring.flyway.enabled=true` in `application.yml`.

**FINDING-024 | HIGH**
No rollback strategy is defined. Section 14.3 describes the CI pipeline (build, test, static analysis) but says nothing about deployment rollback. With `ddl-auto: validate`, a deployment that introduces a schema change and then fails mid-deployment leaves the database in a state incompatible with the previous application version. The previous version will fail validation on startup, causing a complete outage rather than a graceful rollback.

Required change: Add a rollback section to section 14 specifying: (a) Flyway migration rollback procedure, (b) application version rollback (blue-green or redeploy of previous artifact), (c) confirmation that all Flyway migrations in this iteration are backward-compatible with the previous application version (no destructive column removals).

**FINDING-025 | LOW**
The custom metrics defined in section 12.2 (`login.attempts.failed`, `login.attempts.locked`, `token.blacklist.size`) have no defined alerting thresholds. Instrumentation without alerting thresholds is not operationally actionable. At minimum, the architecture should recommend alert conditions for the most security-relevant counter.

Required change (LOW, not blocking): Add a recommended alert thresholds subsection to section 12 (e.g., alert if `login.attempts.locked` exceeds N events per minute, indicating a credential-stuffing attack; alert if `http.server.requests` p95 for `/api/v1/auth/login` exceeds 1800 ms).

---

## Risk Assessment

| Risk | Severity | Finding |
|---|---|---|
| Per-IP rate limiting not achievable with @RateLimiter annotation | CRITICAL | FINDING-006 |
| Spring Security cannot boot without UserDetailsService | CRITICAL | FINDING-007 |
| Package namespace collision with DocSync application | HIGH | FINDING-005 |
| Swagger/API docs exposed publicly in production | HIGH | FINDING-011 |
| Actuator metrics endpoint not access-controlled | HIGH | FINDING-012 |
| No database migration tooling | HIGH | FINDING-023 |
| No rollback strategy | HIGH | FINDING-024 |
| No GlobalExceptionHandlerTest | HIGH | FINDING-019 |
| No JwtAuthenticationFilterTest | HIGH | FINDING-020 |
| Refresh token table grows unboundedly without cleanup task | MEDIUM | FINDING-016 |
| No circuit breaker for DB operations | MEDIUM | FINDING-017 |
| JWT secret minimum length not validated at startup | MEDIUM | FINDING-013 |
| No jti claim; raw token used as blacklist key | MEDIUM | FINDING-014 |
| Refresh token rotation not addressed | MEDIUM | FINDING-003 |
| Multi-device logout behavior undocumented | MEDIUM | FINDING-009 |
| Failed attempt counting for non-existent emails ambiguous | MEDIUM | FINDING-002 |
| RequestCorrelationFilter not designed as component | MEDIUM | FINDING-008 |
| User.enabled lifecycle undefined | LOW | FINDING-010 |
| PII (email) in log output unaddressed | LOW | FINDING-015 |
| Rate limiter test strategy absent | MEDIUM | FINDING-021 |
| Integration test DB strategy absent | MEDIUM | FINDING-022 |
| Alert thresholds not defined | LOW | FINDING-025 |
| Caffeine expiry policy undocumented | LOW | FINDING-018 |

---

## Gaps

1. No `UserDetailsService` component — Spring Security filter chain cannot be assembled without it.
2. No per-IP keying in the rate limiter — NFR-001.4 and AC-010 are unsatisfiable as designed.
3. No database migration path — DDL has no automated execution mechanism.
4. No `GlobalExceptionHandlerTest` or `JwtAuthenticationFilterTest` — critical security path tests absent from test plan.
5. No integration test database isolation strategy (Testcontainers).
6. `enabled` field on `User` entity has no defined lifecycle or exception handler mapping.
7. `RequestCorrelationFilter` referenced in observability but not designed as a component.

---

## Required Changes Before Implementation

The following changes to `architecture.md` are required. All are CRITICAL or HIGH severity. Human approval of the updated `architecture.md` is required before implementation planning proceeds.

### RC-001 (CRITICAL — FINDING-006): Replace annotation-based rate limiter with per-IP filter

Add `IpRateLimiterFilter extends OncePerRequestFilter` to section 4 as a named component. The filter maintains a `LoadingCache<String, RateLimiter>` (Caffeine-backed) keyed by client IP address. Each value is a Resilience4j `RateLimiter` instance with `limit-for-period: 5` and `limit-refresh-period: 60s`. Remove `@RateLimiter` annotations from `AuthController`. Update data flow diagrams in section 8 to show `IpRateLimiterFilter` (not `RateLimiterFilter`). Update the component diagram.

### RC-002 (CRITICAL — FINDING-007): Add UserDetailsService component

Add `UserDetailsServiceImpl implements UserDetailsService` to section 4 (`com.docsync.service`). Document responsibility: load `User` by email from `UserRepository`; throw `UsernameNotFoundException` if absent; return a Spring Security `UserDetails` wrapping the `User` entity. Register via `SecurityConfig.authenticationProvider(DaoAuthenticationProvider)`. Add `UserDetailsServiceImplTest` to section 5.

### RC-003 (HIGH — FINDING-005): Resolve package namespace

Add an explicit statement to section 3 (Recommended Architecture) declaring whether the Login API is: (a) a feature module of the DocSync Spring Boot application sharing `com.docsync` root package, or (b) a standalone Spring Boot application requiring a distinct root package. If (a), confirm that the Login API packages (`com.docsync.controller.AuthController`, etc.) do not collide with existing DocSync components. If (b), rename root package throughout.

### RC-004 (HIGH — FINDING-011): Restrict Swagger in production

Add `application-prod.yml` to the deployment section specifying `springdoc.api-docs.enabled: false` and `springdoc.swagger-ui.enabled: false`. Update section 10.1 to note that public Swagger endpoints apply to non-production profiles only.

### RC-005 (HIGH — FINDING-012): Control actuator endpoint access

Update section 14.2 `application.yml` to either set `management.server.port` to a separate port (e.g., 8081) or add Spring Security rules restricting `/actuator/metrics` and `/actuator/info` to authenticated internal callers. Update section 10 security boundaries.

### RC-006 (HIGH — FINDING-023): Add Flyway migration tooling

Add Flyway to the technology stack (section 9). Specify `spring.flyway.enabled=true` in `application.yml`. Move DDL from section 7 to a versioned migration file path `src/main/resources/db/migration/V1__create_auth_tables.sql`. Update section 14.3 CI pipeline to include Flyway migration as a deployment step.

### RC-007 (HIGH — FINDING-024): Define rollback strategy

Add a rollback subsection to section 14 specifying application artifact rollback and Flyway migration compatibility requirements.

### RC-008 (HIGH — FINDING-019 + FINDING-020): Add missing test classes

Add `GlobalExceptionHandlerTest` and `JwtAuthenticationFilterTest` to the test structure in section 5 with scoped test descriptions.

---

## Approved Decisions

The following design decisions are approved and need no further review:

| Decision | ADR | Rationale |
|---|---|---|
| Classic layered monolith over hexagonal/CQRS | Section 3 | Correct choice for single bounded context; lowest complexity; testable |
| Caffeine in-memory blacklist with Redis migration path | ADR-002 | Appropriate for single-instance; trade-off explicitly documented |
| PostgreSQL 15+ over MySQL | ADR-003 | Native UUID, better standards compliance; Redis migration path clean |
| io.jsonwebtoken:jjwt 0.12.6 | ADR-004 | Current stable, Java 17 support, split API/impl modules |
| rememberMe extends refresh token only (not access token) | ADR-001 | Correct security decision; limits blast radius of compromised access token |
| Email verification out of scope | ADR-005 | Aligns with requirements A-003 |
| Failure counter resets on successful login | ADR-006 | Standard behavior; avoids locking legitimate users |
| Single ROLE_USER with array claim for forward compatibility | ADR-007 | Forward-compatible; no roles table needed this iteration |
| BCrypt cost factor 10 | Section 10.3 | ~100-200 ms; well within p95 latency targets |
| ddl-auto: validate in production | Section 14.2 | Correct production safety default |
| Java 17 records for all DTOs | Section 4.12 | Idiomatic Java 17; immutable value objects |
| Constructor injection enforced | Section 4 preamble | Compliant with CLAUDE.md; testable |

---

## Open Questions (Forwarded to Architecture Agent)

| ID | Question | Required By |
|---|---|---|
| OQ-A | Is the Login API a feature of the DocSync application (same `com.docsync` package, same Spring Boot application context) or a standalone service? | RC-003 |
| OQ-B | Should failed login attempts against non-existent email addresses be counted toward the lockout? | RC-001 (FINDING-002) |
| OQ-C | Is refresh token rotation required? If not, add an ADR accepting the no-rotation stance. | FINDING-003 |
| OQ-D | Should `/actuator/metrics` and `/actuator/info` be network-restricted or authentication-gated? | RC-005 |

---

**Next Step**: Architecture agent must update `architecture.md` to resolve RC-001 through RC-008. Updated `architecture.md` requires human approval (Suraj Salunkhe) before implementation planning begins. Per CLAUDE.md, no implementation activity may start until the revised architecture is approved.
