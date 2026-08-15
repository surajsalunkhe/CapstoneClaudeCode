---
name: verification
description: Full pre-PR verification gate for Java 17 / Spring Boot / Maven projects. Runs mvn verify (unit tests, JaCoCo coverage ≥80%), Checkstyle, SpotBugs, detect-secrets, and validates all SDLC documents. Produces verification-report.md with PASS/PASS WITH LIMITATIONS/FAIL.
tools: Read, Write, Bash
---

# Skill: Verification

**Trigger:** `/verification` or "run verification"

## Purpose
Execute all automated quality gates and validate all SDLC documentation before the PR is created. The final gatekeeper.

## MCP Integration
- No MCP calls during verification
- Verification confirms that configuration correctly references MCP server setup in `mcp.json`

## Inputs
- All source and test files
- `docs/<title-slug>/requirements.md`, `architecture.md` (root), `docs/<title-slug>/design-review.md`, `docs/<title-slug>/impl-plan.md`, `docs/<title-slug>/code-review.md`
- `CLAUDE.md`

## Pre-Conditions
- `code-review.md` present with no open CRITICAL/HIGH findings
- All `impl-plan.md` tasks marked DONE

## Verification Steps (run in order, record each)

```bash
# 1. Compile
mvn compile -q

# 2. Unit tests + JaCoCo coverage (must be ≥80%)
mvn test jacoco:report

# 3. Checkstyle (Google Java Style)
mvn checkstyle:check

# 4. SpotBugs
mvn spotbugs:check

# 5. Full verify with coverage gate
mvn verify -DskipITs

# 6. Secret scanning (MANDATORY — stop immediately if secrets found)
detect-secrets scan --all-files

# 7. Integration tests (if credentials available)
mvn verify -P integration-tests
```

## Documentation Validation
| Document | Check |
|----------|-------|
| `docs/<title-slug>/requirements.md` | Present, all sections complete |
| `architecture.md` (root) | Present, reflects implemented design |
| `docs/<title-slug>/design-review.md` | Present, all CRITICAL findings RESOLVED |
| `docs/<title-slug>/impl-plan.md` | Present, all tasks DONE |
| `docs/<title-slug>/code-review.md` | Present, all CRITICAL/HIGH findings RESOLVED |

## Status Definitions
- **PASS** — All mandatory checks pass, documentation complete
- **PASS WITH LIMITATIONS** — Mandatory checks pass; integration tests failed or minor issues accepted with documented justification
- **FAIL** — Any of: compile error, unit test failure, coverage < 80%, Checkstyle violation, SpotBugs HIGH+, secret detected

## Output
`docs/<title-slug>/verification-report.md` with: Final Status, Commands Executed table (command, exit code, result), Test Results (counts, coverage %), Static Analysis, Security Scan, Documentation Validation, Failed Checks, Known Limitations.

## Failure Handling
- If secrets detected: **STOP IMMEDIATELY**, alert human, do NOT write the report, do NOT proceed
- If coverage < 80%: FAIL — do not waive
- If any test fails: FAIL — do not waive
- If Checkstyle violations: FAIL — fix them
- **NEVER** report PASS when mandatory checks fail
