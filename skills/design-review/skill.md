---
name: design-review
description: Adversarial review of requirements.md + architecture.md across 6 dimensions (requirements alignment, architecture quality, security, reliability, testing, operations). Produces design-review.md with CRITICAL/HIGH/MEDIUM/LOW findings and a verdict.
tools: Read, Write, Bash
---

# Skill: Design Review

**Trigger:** `/design-review` or "review the design"

## Purpose
Perform an independent, adversarial structured review of the proposed architecture against approved requirements, finding problems before implementation begins.

## MCP Integration (reviewed, not called)
Verify that `architecture.md` correctly specifies how MCP tools will be used:
- `mcp__github__list_commits` — correct? scoped to right repo?
- `mcp__github__get_file_contents` — needed? redundant with MCP commits?
- `mcp__confluence__get_page` — used before update to get current version?
- `mcp__confluence__update_page` — idempotency considered?
- `mcp__confluence__create_page` — when triggered? guarded against duplicates?

## Inputs
- `docs/<title-slug>/requirements.md` (approved) — slug provided by requirements-analysis skill
- `architecture.md` (project root)
- `CLAUDE.md`

## Review Areas

### 1. Requirements Alignment
- Every FR has a component and implementation path
- Every NFR has measurable target and strategy
- Every AC is testable as designed

### 2. Architecture Quality
- Single-responsibility components, no circular deps
- REST API contracts complete (request, response, error codes)
- Data flow end-to-end described

### 3. Security
- No credentials in source or config
- External inputs validated (Bean Validation)
- `UriComponentsBuilder` for URLs, not string concatenation
- HTTP timeouts specified
- Sensitive fields not logged

### 4. Reliability
- Error handling for each MCP call
- Retry/timeout policies defined
- Partial failures recorded in `SyncReport.errors`, not propagated
- Idempotency considered

### 5. Testing Feasibility
- MockMvc usable for controllers
- MockWebServer usable for HTTP clients
- Mockito usable for service unit tests
- Integration test path defined

### 6. Operational Readiness
- `/actuator/health` exposed (not full actuator)
- Structured logging with correlation IDs
- Deployment documented
- Rollback possible

## Severity Levels
| Level | Action |
|-------|--------|
| CRITICAL | Blocks implementation; must resolve before planning |
| HIGH | Must resolve; architecture update required |
| MEDIUM | Should resolve; design decision needed |
| LOW | Recommended improvement |
| INFO | Observation |

## Procedure

1. Read all three inputs.
2. For each review area, produce numbered findings (FINDING-NNN).
3. Classify severity. Write required changes for CRITICAL/HIGH.
4. Determine overall verdict: `APPROVED` / `APPROVED WITH CHANGES REQUIRED` / `REJECTED`.
5. Write `docs/<title-slug>/design-review.md`.
6. Present findings to human. CRITICAL/HIGH must be resolved before planning.
7. If changes needed: architecture agent updates `architecture.md` (root), then re-review affected areas.

## Output
`docs/<title-slug>/design-review.md` with: Verdict, Findings (per area), Required Changes, Approved Decisions, Risks.

## Failure Handling
- If architecture is fundamentally flawed: return REJECTED verdict with clear explanation.
- If required changes invalidate other parts of the architecture: flag cascading impact.
