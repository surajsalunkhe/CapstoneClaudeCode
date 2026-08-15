---
name: code-review
description: Adversarial peer code review for Java 17 / Spring Boot / Maven projects. Reviews correctness, security, error handling, test coverage, code clarity, DRY violations, and dependency safety. Uses GitHub MCP to read remote files. Produces code-review.md.
tools: Read, Write, Bash, mcp__github__get_file_contents, mcp__github__list_commits
---

# Skill: Code Review

**Trigger:** `/code-review` or "review the code"

## Purpose
Perform an adversarial peer code review of the Java 17 / Spring Boot implementation, checking correctness, security, error handling, test coverage, code clarity, DRY compliance, and dependency safety.

## MCP Integration
- `mcp__github__get_file_contents` — read specific source files from GitHub for remote review
- `mcp__github__list_commits` — inspect recent commits to understand change scope

## Inputs
- All files in `src/main/java/` and `src/test/java/`
- `docs/<title-slug>/requirements.md`, `architecture.md` (root), `docs/<title-slug>/impl-plan.md`, `CLAUDE.md`
- Output of `git diff main...HEAD`

## Review Checklist

### Correctness
- [ ] All FRs from `requirements.md` implemented
- [ ] All ACs testable as designed
- [ ] All `impl-plan.md` tasks marked DONE

### Security
- [ ] No credentials, tokens, secrets in source code
- [ ] All inputs validated (`@Valid`, `@NotBlank`, `@Pattern`)
- [ ] `UriComponentsBuilder` used for all URL construction
- [ ] HTTP calls have explicit connect + read timeouts in `AppConfig`
- [ ] Sensitive fields not logged

### Error Handling
- [ ] Typed exceptions: `GitHubAuthException`, `ConfluenceNotFoundException`, `DocSyncException`
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`) covers all types
- [ ] No bare `catch (Exception e) { }` blocks
- [ ] Partial failures in `SyncReport.errors`, not re-thrown

### Test Coverage
- [ ] Happy path, invalid input, not-found, empty, error, boundary — all covered
- [ ] WebClient clients tested with `MockWebServer` (OkHttp)
- [ ] Controllers tested with `MockMvc` + `@WebMvcTest`
- [ ] JaCoCo ≥ 80% line coverage

### Code Clarity
- [ ] SLF4J `LOG` (uppercase) everywhere — no `System.out.println`
- [ ] Constructor injection only — no field `@Autowired`
- [ ] Java 17 records for domain models
- [ ] Javadoc on all public service and controller methods

### DRY
- [ ] No duplicate validation logic
- [ ] No repeated URL-building patterns

### Dependency Safety
- [ ] No known CVEs in `pom.xml`
- [ ] No unnecessary dependencies

## Procedure

1. Read `CLAUDE.md` and all SDLC documents.
2. Use `mcp__github__list_commits` to understand recent changes if needed.
3. Use `mcp__github__get_file_contents` for remote file access if needed.
4. Read all source and test files locally.
5. Work through each checklist section systematically.
6. Write a numbered finding (CR-NNN) for each issue.
7. Classify severity: CRITICAL | HIGH | MEDIUM | LOW | INFO.
8. Write `code-review.md`.
9. Present to human. CRITICAL/HIGH must be fixed before PR.

## Output
`docs/<title-slug>/code-review.md` with: Summary verdict, Findings (CR-NNN with file + line), Required Changes.

## Failure Handling
- If secrets found: immediately stop and alert human. Do not proceed.
- If coverage < 80%: flag as CRITICAL.
- If a required AC is not implemented: flag as CRITICAL.
