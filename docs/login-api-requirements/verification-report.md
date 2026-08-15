# Verification Report

**Feature**: Login API
**Verifier**: Verification Agent (Claude Sonnet 4.6)
**Date**: 2026-08-15
**Project Root**: `/Users/suraj_shivajisalunkhe/CapstoneClaudeCode`

---

## Final Status: FAIL

The Login API implementation fails the mandatory SpotBugs quality gate (`mvn spotbugs:check` exits 1 with 10 EI_EXPOSE_REP2 bugs). Per constraint C-004 in requirements.md and the Definition of Done in CLAUDE.md, SpotBugs must pass with zero errors before merging. Additionally, three blocking issues identified by the code review (CR-001 through CR-003) remain OPEN and unresolved. No PR may be created until these gates pass.

---

## Commands Executed

| # | Command | Exit Code | Result |
|---|---|---|---|
| 1 | `mvn compile` | 0 | PASS |
| 2 | `mvn test` | 0 | PASS — 73 tests, 0 failures, 0 errors |
| 3 | `mvn test jacoco:report` | 0 | PASS — 88% instruction coverage (threshold: 80%) |
| 4 | `mvn verify -DskipITs` | 0 | PASS — JaCoCo coverage gate satisfied |
| 5 | `mvn verify -P integration-tests` | 0 | PASS WITH LIMITATIONS — see note below |
| 6 | `mvn checkstyle:check` | 0 | PASS — 0 violations |
| 7 | `mvn spotbugs:check` | 1 | **FAIL** — 10 EI_EXPOSE_REP2 bugs |
| 8 | `detect-secrets scan --all-files` | 127 | SKIPPED — `detect-secrets` not installed on this machine; manual review performed instead |

---

## Test Results

- **Total unit tests**: 73
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0

### Per-class breakdown

| Test Class | Tests | Result |
|---|---|---|
| `com.docsync.config.IpRateLimiterFilterTest` | 4 | PASS |
| `com.docsync.config.RequestCorrelationFilterTest` | 3 | PASS |
| `com.docsync.config.JwtAuthenticationFilterTest` | 5 | PASS |
| `com.docsync.config.JwtPropertiesTest` | 4 | PASS |
| `com.docsync.controller.AuthControllerTest` | 11 | PASS |
| `com.docsync.service.TokenBlacklistServiceTest` | 3 | PASS |
| `com.docsync.service.UserDetailsServiceImplTest` | 2 | PASS |
| `com.docsync.service.RefreshTokenCleanupTaskTest` | 2 | PASS |
| `com.docsync.service.AuthServiceTest` | 15 | PASS |
| `com.docsync.service.JwtServiceTest` | 9 | PASS |
| `com.docsync.service.LoginAttemptServiceTest` | 5 | PASS |
| `com.docsync.exception.GlobalExceptionHandlerTest` | 10 | PASS |

---

## JaCoCo Coverage Results

Overall instruction coverage: **88%** (threshold: 80%) — PASS

| Package | Instruction Coverage | Branch Coverage | Notes |
|---|---|---|---|
| `com.docsync.service` | 98% | 88% | Well covered |
| `com.docsync.model.response` | 100% | n/a | Full coverage |
| `com.docsync.controller` | 100% | 75% | Branch gap on logout null-check |
| `com.docsync.model.request` | 100% | n/a | Full coverage |
| `com.docsync.exception` | 95% | n/a | Near complete |
| `com.docsync.config` | 74% | 80% | Below instruction threshold but overall bundle passes |
| `com.docsync.model.entity` | 58% | n/a | Entity getters/lifecycle not directly tested |
| `com.docsync` (root) | 37% | n/a | Application main class low coverage; minimal impact |

JaCoCo coverage gate (`mvn verify`) passes because the **bundle-level** 80% instruction threshold is met at 88%. The `com.docsync.model.entity` package at 58% and `com.docsync.config` at 74% are below their package-level ideal but do not cause the overall gate to fail.

---

## Static Analysis Results

### Checkstyle

- **Result**: PASS
- **Violations**: 0
- **Standard enforced**: Google Java Style, 120-character line limit

### SpotBugs

- **Result**: FAIL
- **Total bugs**: 10
- **Bug pattern**: `EI_EXPOSE_REP2` / `EI_EXPOSE_REP` (MALICIOUS_CODE, Medium, rank 18)
- **Build exit code**: 1 — `mvn spotbugs:check` and `mvn verify` (without `-DskipITs`) would fail this gate

| # | File | Line | Pattern | Description |
|---|---|---|---|---|
| 1 | `SecurityConfig.java` | 56 | EI_EXPOSE_REP2 | Storing `JwtAuthenticationFilter` (mutable) into field |
| 2 | `SecurityConfig.java` | 57 | EI_EXPOSE_REP2 | Storing `IpRateLimiterFilter` (mutable) into field |
| 3 | `RefreshToken.java` | 97 | EI_EXPOSE_REP | `getUser()` returns mutable `User` reference |
| 4 | `RefreshToken.java` | 106 | EI_EXPOSE_REP2 | `setUser(User)` stores externally mutable object |
| 5 | `ApiResponse.java` | 16 | EI_EXPOSE_REP | `errors()` accessor returns mutable `List` |
| 6 | `ApiResponse.java` | 16 | EI_EXPOSE_REP2 | Constructor stores externally mutable `List` |
| 7 | `AuthService.java` | 72 | EI_EXPOSE_REP2 | Storing `RefreshTokenRepository` (mutable) into field |
| 8 | `JwtService.java` | 32 | EI_EXPOSE_REP2 | Storing `JwtProperties` (mutable) into field |
| 9 | `LoginAttemptService.java` | 44 | EI_EXPOSE_REP2 | Storing `MeterRegistry` (mutable) into field |
| 10 | `RefreshTokenCleanupTask.java` | 29 | EI_EXPOSE_REP2 | Storing `RefreshTokenRepository` (mutable) into field |

Items 1–2, 7–10: False positives — Spring beans stored via constructor injection are intentional. Fix: add a `spotbugs-exclude.xml` filter and reference it in `pom.xml`.
Items 3–6: Legitimate findings. Fix `ApiResponse.errors()` with `List.copyOf()` in the canonical record constructor; add `@SuppressFBWarnings` or an exclude-filter entry for the JPA entity's `getUser()`/`setUser()` (which cannot be made immutable as they are required for JPA).

---

## Integration Test Results

`mvn verify -P integration-tests` exits 0. However, the integration test classes (`AuthIntegrationTest.java`, `AuthRateLimiterIntegrationTest.java`) were **not executed** by Maven Failsafe during this run. The Failsafe plugin is configured with `<groups>integration</groups>` to pick up classes tagged `@Tag("integration")`, but no integration test output appeared in the build log. This indicates that:

1. Docker is not available on this machine (`docker` command not found) — Testcontainers requires Docker to start a PostgreSQL 15 container.
2. Failsafe reported 0 integration tests run; the build succeeded because there were no failures, not because the tests passed.

**Conclusion**: Integration tests exist and are correctly structured but were not verified in this environment due to the absence of Docker / Testcontainers infrastructure.

---

## Secret Scanning Results

`detect-secrets` is not installed on this machine (exit code 127). A manual inspection of committed files was performed instead.

### Manual Findings

| File | Finding | Severity |
|---|---|---|
| `src/main/resources/application.yml` line 25 | `jwt.secret: ${JWT_SECRET:default-secret-for-local-dev-only-replace-in-production}` — hardcoded fallback default committed to repository | HIGH (CR-004) |
| `src/main/resources/application.yml` lines 6–8 | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` also have hardcoded fallback defaults (`logindb`, `loginuser`, `loginpass`) | MEDIUM |

The JWT secret fallback (`default-secret-for-local-dev-only-replace-in-production`) is 47 characters — it passes the `JwtProperties.validateSecret()` length check at startup. Any instance started without `JWT_SECRET` in the environment uses this well-known string as the signing key. This violates NFR-001.5 and C-003.

No actual credential strings (tokens, passwords, private keys) were found hard-coded in Java source files.

---

## Documentation Validation

| Document | Path | Present | Non-empty | Status Notes |
|---|---|---|---|---|
| `requirements.md` | `docs/login-api-requirements/requirements.md` | YES | YES (256 lines) | Status: APPROVED. All sections complete: user story, FR, NFR, AC, constraints, dependencies, open questions. |
| `design-review.md` | `docs/login-api-requirements/design-review.md` | YES | YES (289 lines) | Status: APPROVED WITH CHANGES REQUIRED. Contains RC-001 through RC-008 required changes. |
| `impl-plan.md` | `docs/login-api-requirements/impl-plan.md` | YES | YES (1385 lines) | Status: DONE. All 20 tasks listed. Note: document header still says "PENDING HUMAN APPROVAL" for architecture.md, which remains a governance gap (CR-014). |
| `code-review.md` | `docs/login-api-requirements/code-review.md` | YES | YES (227 lines) | Status: APPROVED WITH COMMENTS. CR-001/CR-002/CR-003 are HIGH severity and OPEN — blocking conditions for PR. |
| `architecture.md` | `architecture.md` (root) | YES | YES (957 lines) | Status: PENDING HUMAN APPROVAL. Implementation was performed before formal approval was recorded. |
| `verification-report.md` | `docs/login-api-requirements/verification-report.md` | YES (this file) | YES | Created by this verification run. |

---

## Failed Checks

### FAIL-1: SpotBugs Gate Broken (CR-001)

**Gate**: `mvn spotbugs:check`
**Exit Code**: 1
**Detail**: 10 `EI_EXPOSE_REP2` / `EI_EXPOSE_REP` bugs across 7 source files. The SpotBugs Maven plugin is configured to fail on HIGH/MEDIUM bugs (`<threshold>Medium</threshold>`), and all 10 findings are Medium rank. Per requirements constraint C-004 and CLAUDE.md Definition of Done, SpotBugs must pass with zero errors.
**Blocker**: YES — this is the primary gate preventing PR creation.

### FAIL-2: Password Mismatch Returns HTTP 401 Instead of 400 (CR-002)

**Gate**: Correctness / AC-003
**Detail**: `AuthService.register()` throws `InvalidCredentialsException` on password mismatch. `GlobalExceptionHandler` maps `InvalidCredentialsException` to HTTP 401. FR-001.6 and AC-003 require HTTP 400 for password mismatch. No controller-level test covers this path (CR-009).
**Blocker**: YES — correctness violation against an approved acceptance criterion.

### FAIL-3: X-Forwarded-For Spoofing Bypasses Rate Limiter (CR-003)

**Gate**: Security / NFR-001.4 / AC-010
**Detail**: `IpRateLimiterFilter.resolveClientIp()` trusts the `X-Forwarded-For` header unconditionally. Any client can send a different IP on every request, bypassing the per-IP rate limit entirely.
**Blocker**: YES — rate limiting (NFR-001.4, AC-010) is trivially circumventable.

### FAIL-4: Secret Scan Tool Not Available

**Gate**: `detect-secrets scan --all-files`
**Exit Code**: 127 (command not found)
**Detail**: `detect-secrets` is not installed. Manual review found a committed JWT secret default fallback (CR-004) and DB credential defaults in `application.yml`.
**Blocker**: The tooling gap is a limitation; CR-004 itself is a blocking issue that must be resolved independently.

### FAIL-5: Integration Tests Not Executed (Docker Unavailable)

**Gate**: `mvn verify -P integration-tests` (functional, but Testcontainers requires Docker)
**Detail**: Docker is not present on the verification machine. Failsafe ran 0 integration tests. The end-to-end flows (register → login → logout → token invalidation, account lockout, rate limiter) were not validated against a real PostgreSQL instance.
**Blocker**: Not a code defect — a CI infrastructure gap. Integration tests must be executed in a Docker-capable environment before the PR is created.

---

## Known Limitations

| Limitation | Justification | Accepted? |
|---|---|---|
| `detect-secrets` not installed | Tool not present on this machine. Manual inspection substituted. | PARTIAL — manual review found CR-004; full automated scan must be performed before PR. |
| Docker not available for Testcontainers | Integration tests require Docker to run PostgreSQL 15 containers. Tests exist and are correctly annotated but could not be executed here. | PARTIAL — must be verified in a Docker-capable CI environment before PR. |
| `com.docsync.model.entity` package at 58% instruction coverage | Entity getters and lifecycle callbacks not unit-tested; exercised by integration tests. The bundle-level 80% threshold still passes at 88%. | ACCEPTED conditionally — integration tests covering the JPA lifecycle must pass first. |
| `architecture.md` status "PENDING HUMAN APPROVAL" | Implementation was performed before the governance gate was formally closed. Code faithfully implements the architecture document, but approval was not recorded. | NOT ACCEPTED — per CLAUDE.md, human approval must be recorded before PR. |

---

## Code Review Issue Tracker

| ID | Severity | Description | Status | Blocks PR? |
|---|---|---|---|---|
| CR-001 | HIGH | SpotBugs fails with 10 EI_EXPOSE_REP2 bugs — add `spotbugs-exclude.xml` filter; fix `ApiResponse.errors()` with `List.copyOf()` | OPEN | YES |
| CR-002 | HIGH | `POST /register` password mismatch returns 401 (must be 400) — replace `InvalidCredentialsException` with a 400-mapped exception in `AuthService.register()` | OPEN | YES |
| CR-003 | HIGH | `X-Forwarded-For` trusted unconditionally in `IpRateLimiterFilter` — restrict to trusted proxy CIDR allowlist or always use `request.getRemoteAddr()` | OPEN | YES |
| CR-004 | MEDIUM | Default JWT secret fallback in `application.yml` violates NFR-001.5 — remove fallback so `${JWT_SECRET}` has no default | OPEN | YES (security) |
| CR-005 | MEDIUM | `catch (Exception e)` in `JwtAuthenticationFilter` — replace with `catch (JwtException | IllegalArgumentException e)` | OPEN | Recommended before PR |
| CR-006 | MEDIUM | `AuthController.logout()` passes `null` token to `AuthService` when header is absent — add explicit null check | OPEN | Recommended before PR |
| CR-007 | MEDIUM | `logout_invalidToken_returns200()` test validates unreachable production path — rename/document | OPEN | Low |
| CR-008 | MEDIUM | `GlobalExceptionHandler.handleValidation()` uses `Collectors.toList()` — replace with `.toList()` (Java 16+) | OPEN | Low |
| CR-009 | MEDIUM | Missing `register_passwordMismatch_returns400()` controller test | OPEN | YES (paired with CR-002) |
| CR-010 | MEDIUM | `resolveClientIp()` is package-private for test access — refactor test to use filter's public contract | OPEN | Low |
| CR-011 | LOW | `com.docsync.model.entity` at 58% coverage — entity lifecycle not unit-tested | OPEN | No (covered by integration tests) |
| CR-012 | LOW | `UsernameNotFoundException` embeds email (PII) in message — remove email from message text | OPEN | No |
| CR-013 | LOW | `RateLimiterConfig` class name collides with Resilience4j class — rename to `AuthRateLimiterConfig` | OPEN | No |
| CR-014 | INFO | `architecture.md` and `impl-plan.md` show "PENDING HUMAN APPROVAL" — governance gate was bypassed | OPEN | YES (per CLAUDE.md) |
| CR-015 | INFO | `GlobalExceptionHandler` catch-all intercepts Spring MVC exceptions returning 500 instead of 400/405 | OPEN | No |
| CR-016 | INFO | Integration tests not evaluated during code review (requires Testcontainers/Docker) | OPEN | YES (must run before PR) |

---

## Recommended Next Steps

The following actions must be completed in order before a PR can be created:

1. **Fix CR-001 (BLOCKING)**: Create `spotbugs-exclude.xml` at project root with entries to suppress Spring-bean false-positive `EI_EXPOSE_REP2` findings. Fix the legitimate `ApiResponse.errors()` finding by wrapping the list in `List.copyOf()` inside the canonical record constructor. Reference the filter file in `pom.xml` via `<excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>`. Verify `mvn spotbugs:check` exits 0.

2. **Fix CR-002 + CR-009 (BLOCKING)**: In `AuthService.register()`, replace `throw new InvalidCredentialsException("Passwords do not match")` with either `throw new IllegalArgumentException("Passwords do not match")` (already mapped to HTTP 400 in `GlobalExceptionHandler`) or create a dedicated `PasswordMismatchException` mapped to 400. Add `register_passwordMismatch_returns400()` to `AuthControllerTest`.

3. **Fix CR-003 (BLOCKING)**: Update `IpRateLimiterFilter.resolveClientIp()` to only honour `X-Forwarded-For` when `request.getRemoteAddr()` matches a configurable allowlist of trusted proxy CIDR ranges. Alternatively, always use `request.getRemoteAddr()` and document that the deployment requires a trusted reverse proxy that rewrites the remote address.

4. **Fix CR-004 (Security — required before PR)**: Remove the default fallback from `jwt.secret` in `application.yml`. Change line 25 from `secret: ${JWT_SECRET:default-secret-for-local-dev-only-replace-in-production}` to `secret: ${JWT_SECRET}`. The `@PostConstruct` in `JwtProperties` will then prevent startup when the variable is absent. Ensure local dev `.env` or docker-compose supplies `JWT_SECRET` explicitly.

5. **Obtain human approval for `architecture.md` (CR-014)**: Per CLAUDE.md, Suraj Salunkhe must explicitly approve `architecture.md` and the approval must be recorded in the document (status: APPROVED, approver, date) before PR creation.

6. **Run integration tests in a Docker-capable environment**: Execute `mvn verify -P integration-tests` with Docker available so Testcontainers can provision a PostgreSQL 15 instance. Confirm `AuthIntegrationTest` and `AuthRateLimiterIntegrationTest` both pass.

7. **Install and run `detect-secrets`**: Run `detect-secrets scan --all-files` after resolving CR-004. Confirm no secrets are reported.

8. **Address recommended fixes (CR-005, CR-006, CR-008)** before PR to improve code quality and reduce future risk:
   - CR-005: Narrow `catch (Exception e)` in `JwtAuthenticationFilter` to `catch (JwtException | IllegalArgumentException e)`.
   - CR-006: Add null-check guard in `AuthController.logout()`.
   - CR-008: Replace `Collectors.toList()` with `.toList()` in `GlobalExceptionHandler`.

Once all blocking items (1–7) are resolved and all gates pass, the PR agent may be invoked to create the pull request.
