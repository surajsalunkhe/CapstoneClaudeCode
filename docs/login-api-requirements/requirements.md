# Requirements: Login API

**Status**: APPROVED
**Source**: [Confluence — Login API requirements](https://sssalunkhe.atlassian.net/wiki/spaces/Sun/pages/2031617/Login+API+requirements)
**Output path**: `docs/login-api-requirements/`
**Date**: 2026-08-15

---

## User Story

As a user, I want to register and login to the application so that I can access personalized features and secure my data.

---

## Business Objective

Provide a secure, standards-compliant authentication layer (register, login, logout, token refresh) that protects user data, prevents unauthorized access, and delivers a responsive experience across devices. The login API is the entry point for all authenticated features in the application.

---

## Actors

| Actor | Description |
|---|---|
| Anonymous User | A visitor who has not yet registered or logged in |
| Registered User | A user with a verified account who can log in |
| System | The Spring Boot backend enforcing security rules |

---

## Functional Requirements

### FR-001: User Registration

- FR-001.1: The system shall expose `POST /api/v1/auth/register` to create a new user account.
- FR-001.2: The request body shall accept: `email`, `password`, `confirmPassword`, `acceptTerms`.
- FR-001.3: `email` must be non-blank and in valid RFC-5322 email format.
- FR-001.4: `email` must be unique across all registered accounts; duplicate emails shall return HTTP 409.
- FR-001.5: `password` must be at minimum 8 characters and contain at least one uppercase letter, one lowercase letter, one digit, and one special character from `@$!%*?&`.
- FR-001.6: `confirmPassword` must exactly match `password`; mismatch shall return HTTP 400.
- FR-001.7: `acceptTerms` must be `true`; a `false` or absent value shall return HTTP 400.
- FR-001.8: On success the system shall return HTTP 201 with `userId` (UUID), `email`, and `createdAt` timestamp.
- FR-001.9: Passwords shall never be returned in any response payload.

### FR-002: User Login

- FR-002.1: The system shall expose `POST /api/v1/auth/login` to authenticate a registered user.
- FR-002.2: The request body shall accept: `email`, `password`, `rememberMe` (boolean, optional).
- FR-002.3: The system shall validate credentials against stored hashed passwords using BCrypt.
- FR-002.4: On successful authentication the system shall return HTTP 200 with an `accessToken` (JWT), `refreshToken` (JWT), `tokenType` ("Bearer"), `expiresIn` (seconds), and basic user info.
- FR-002.5: Invalid credentials (wrong password or unknown email) shall return HTTP 401 with a generic message ("Invalid email or password") — no enumeration of whether email or password was the failing factor.
- FR-002.6: Failed login attempts shall be counted per account. After 5 consecutive failures the account shall be locked for 15 minutes and subsequent attempts shall return HTTP 403.
- FR-002.7: When `rememberMe` is `true`, the access token or refresh token expiry shall be extended beyond the default session lifetime (exact duration — see OQ-001).

### FR-003: Session Management

- FR-003.1: Access tokens shall expire after 30 minutes of inactivity (1 800 000 ms).
- FR-003.2: Refresh tokens shall expire after 7 days (604 800 000 ms).
- FR-003.3: The system shall expose `POST /api/v1/auth/refresh` to issue a new access token when presented with a valid, non-expired refresh token.
- FR-003.4: A refreshed access token shall return HTTP 200 with the new `accessToken` and `expiresIn`.
- FR-003.5: An invalid or expired refresh token shall return HTTP 401.

### FR-004: Logout

- FR-004.1: The system shall expose `POST /api/v1/auth/logout` requiring a valid `Authorization: Bearer {token}` header.
- FR-004.2: On logout the system shall invalidate the presented token (server-side blacklist or token revocation store — see OQ-002).
- FR-004.3: Subsequent requests using the invalidated token shall be rejected with HTTP 401.
- FR-004.4: Successful logout shall return HTTP 200 with message "Logged out successfully".

---

## Non-Functional Requirements

### NFR-001: Security

- NFR-001.1: All passwords at rest shall be hashed with BCrypt (minimum cost factor 10).
- NFR-001.2: All API traffic shall be served over HTTPS; HTTP requests shall be rejected or redirected.
- NFR-001.3: CSRF protection shall be enabled for any session-cookie-based flow; stateless JWT flows are exempt but must validate token signatures on every request.
- NFR-001.4: Rate limiting shall be applied to all `/api/v1/auth/*` endpoints: max 5 requests per minute per IP (configurable via Resilience4j).
- NFR-001.5: JWT secrets shall not be hard-coded; they must be supplied via environment variables or a secrets manager.
- NFR-001.6: All request inputs must be validated server-side using Jakarta Bean Validation before processing.

### NFR-002: Performance

- NFR-002.1: The login endpoint (`POST /api/v1/auth/login`) shall respond in under 2 seconds at the 95th percentile under normal load.
- NFR-002.2: The registration endpoint (`POST /api/v1/auth/register`) shall respond in under 3 seconds at the 95th percentile under normal load.

### NFR-003: Reliability

- NFR-003.1: The authentication service shall achieve 99.9% uptime during business hours.
- NFR-003.2: All database operations shall use connection pooling (HikariCP).
- NFR-003.3: The service shall gracefully handle database unavailability and return HTTP 503 rather than leaking stack traces.

### NFR-004: Usability

- NFR-004.1: All validation error responses shall include field-level error messages indicating which field failed and why.
- NFR-004.2: API responses shall use a consistent envelope: `{ "success": boolean, "message": string, "data": object | null, "errors": array | null }`.

### NFR-005: Testability

- NFR-005.1: All service and controller public methods shall have unit tests covering happy path, invalid input, not-found, empty data, and error scenarios.
- NFR-005.2: Minimum test coverage: 80% (enforced by JaCoCo).
- NFR-005.3: Integration tests shall be annotated `@Tag("integration")` and run separately from unit tests.
- NFR-005.4: The API shall expose an OpenAPI 3.0 (Swagger) specification for consumer-driven contract testing.

---

## Assumptions

| ID | Assumption |
|---|---|
| A-001 | The persistence layer is a relational database (MySQL or PostgreSQL). The exact database engine is to be confirmed in architecture (see OQ-003). |
| A-002 | JWT is the chosen token format; no opaque token or OAuth2 authorization server is in scope for this story. |
| A-003 | Email verification after registration (e.g., confirmation link) is out of scope unless explicitly added. |
| A-004 | The "remember me" feature extends the refresh token lifetime; exact duration is TBD (OQ-001). |
| A-005 | Account lockout resets automatically after 15 minutes; no manual admin unlock flow is in scope. |
| A-006 | Refresh tokens are stored server-side to enable revocation on logout. |

---

## Constraints

| ID | Constraint |
|---|---|
| C-001 | Technology stack is fixed: Java 17, Spring Boot 3.x, Maven 3.9+, Spring Security, JPA/Hibernate. |
| C-002 | Coding standards from CLAUDE.md apply: Google Java Style, 120-char line limit, no field injection, no raw types. |
| C-003 | No secrets may be committed to source control; JWT secret and DB credentials must be environment variables. |
| C-004 | The service must pass Checkstyle and SpotBugs with zero errors before merging. |
| C-005 | SSL/TLS configuration must be externalized and not stored in the repository. |

---

## Dependencies

| ID | Dependency | Type |
|---|---|---|
| D-001 | Spring Security (JWT filter chain) | Library |
| D-002 | BCryptPasswordEncoder | Library (Spring Security) |
| D-003 | Jakarta Bean Validation / Hibernate Validator | Library |
| D-004 | JPA/Hibernate ORM | Library |
| D-005 | MySQL or PostgreSQL database instance | Infrastructure |
| D-006 | Resilience4j RateLimiter | Library |
| D-007 | JWT library (e.g., `io.jsonwebtoken:jjwt`) | Library — version TBD (OQ-004) |
| D-008 | OpenAPI / Springdoc OpenAPI | Library |

---

## Acceptance Criteria

### AC-001: Successful Registration

**Given** a POST request to `/api/v1/auth/register` with a valid email, strong password, matching confirmPassword, and acceptTerms=true
**When** the email does not already exist in the system
**Then** the system returns HTTP 201 with a response body containing `success: true`, a non-null `userId` (UUID format), the registered `email`, and a non-null `createdAt` timestamp.

### AC-002: Duplicate Email on Registration

**Given** a POST request to `/api/v1/auth/register` with an email that already exists
**When** the request is processed
**Then** the system returns HTTP 409 with `success: false` and message "Email already registered".

### AC-003: Weak Password on Registration

**Given** a POST request to `/api/v1/auth/register` with a password that does not meet complexity requirements
**When** the request is processed
**Then** the system returns HTTP 400 with `success: false` and a field-level error on the `password` field.

### AC-004: Successful Login

**Given** a POST request to `/api/v1/auth/login` with valid email and correct password
**When** the account is not locked
**Then** the system returns HTTP 200 with `success: true`, a non-null `accessToken` (JWT), a non-null `refreshToken` (JWT), `tokenType: "Bearer"`, and `expiresIn: 1800`.

### AC-005: Invalid Credentials on Login

**Given** a POST request to `/api/v1/auth/login` with a correct email but wrong password (or unknown email)
**When** the request is processed
**Then** the system returns HTTP 401 with `success: false` and message "Invalid email or password" (no enumeration of which field failed).

### AC-006: Account Lockout

**Given** a registered user account
**When** 5 consecutive login attempts fail
**Then** the system returns HTTP 403 on the 6th attempt with message "Account locked due to too many failed attempts. Try again in 15 minutes."

### AC-007: Successful Logout

**Given** a POST request to `/api/v1/auth/logout` with a valid `Authorization: Bearer {token}` header
**When** the request is processed
**Then** the system returns HTTP 200 with message "Logged out successfully" and subsequent requests using the same token return HTTP 401.

### AC-008: Token Refresh

**Given** a POST request to `/api/v1/auth/refresh` with a valid, non-expired refresh token
**When** the request is processed
**Then** the system returns HTTP 200 with a new `accessToken` and `expiresIn: 1800`.

### AC-009: Expired Refresh Token

**Given** a POST request to `/api/v1/auth/refresh` with an expired or invalid refresh token
**When** the request is processed
**Then** the system returns HTTP 401 with `success: false`.

### AC-010: Rate Limiting

**Given** more than 5 requests per minute from the same IP to any `/api/v1/auth/*` endpoint
**When** the rate limit is exceeded
**Then** the system returns HTTP 429 and does not process the excess request.

---

## Open Questions

| ID | Question | Impact |
|---|---|---|
| OQ-001 | What is the exact token lifetime extension when `rememberMe: true`? (e.g., 7 days vs 30 days for refresh token?) | AC-004, FR-002.7 |
| OQ-002 | How are invalidated tokens tracked on logout? (In-memory blacklist, Redis store, or DB table?) | FR-004.2, infrastructure sizing |
| OQ-003 | Is the target database MySQL or PostgreSQL? | D-005, schema DDL choices |
| OQ-004 | Which JWT library version is approved? (`io.jsonwebtoken:jjwt` vs `com.auth0:java-jwt`) | D-007, implementation |
| OQ-005 | Is email verification (confirmation email after registration) required in this iteration? | A-003, scope |
| OQ-006 | Should failed login attempt counters reset on successful login, or only after the lockout period expires? | FR-002.6 |
| OQ-007 | Are there multiple user roles (e.g., ADMIN, USER) that need to be embedded in the JWT claims, or is this a flat single-role system? | JWT payload design |

---

## Resolved Questions

_None yet — awaiting human review._

---

## Test Scenarios (from Confluence)

The following test case IDs are derived from the source page and map to acceptance criteria above:

| Test Case ID | Description | Maps to AC |
|---|---|---|
| REG-001 | Register with valid data → 201 Created | AC-001 |
| REG-002 | Register with invalid email → 400 | AC-003 |
| REG-003 | Register with weak password → 400 | AC-003 |
| REG-004 | Register with mismatched passwords → 400 | AC-003 |
| REG-005 | Register with existing email → 409 | AC-002 |
| REG-006 | Register without accepting terms → 400 | AC-003 |
| LOGIN-001 | Login with valid credentials → 200 OK, JWT returned | AC-004 |
| LOGIN-002 | Login with invalid password → 401 | AC-005 |
| LOGIN-003 | Login with non-existent email → 401 | AC-005 |
| LOGIN-004 | Login after 5 failed attempts → 403 | AC-006 |
| LOGIN-005 | Login with rememberMe enabled → extended token expiry | OQ-001 pending |
| LOGIN-006 | Logout and verify token invalidated | AC-007 |

---

> **This document is APPROVED.**
>
> **Approval**: Suraj Salunkhe — Date: 2026-08-15
