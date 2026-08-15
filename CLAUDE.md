# CLAUDE.md — Automated Documentation Sync

## Project Purpose

This project implements an **Automated Documentation Sync** service that monitors a GitHub repository for code changes and automatically keeps Confluence documentation synchronized. When developers push code changes, the system detects what changed, extracts documentation-relevant information from the diff, and updates the corresponding Confluence pages — eliminating documentation drift.

## Technology Stack

- **Language**: Java 17 (LTS)
- **Framework**: Spring Boot 3.x
- **Build tool**: Maven 3.9+
- **GitHub integration**: GitHub MCP server (`@modelcontextprotocol/server-github`)
- **Confluence integration**: Atlassian MCP server (`@modelcontextprotocol/server-atlassian`)
- **HTTP client**: Spring WebClient (reactive) for external REST calls
- **Testing**: JUnit 5, Mockito, Spring Boot Test, MockMvc
- **Static analysis**: Checkstyle, SpotBugs
- **Secret scanning**: detect-secrets (pre-commit hook)
- **CI**: GitHub Actions

## Repository Structure

```
project-root/
├── CLAUDE.md
├── architecture.md          ← always at root
├── mcp.json
├── docs/
│   └── <confluence-title-slug>/    ← one folder per requirement, named from Confluence page title
│       ├── requirements.md
│       ├── design-review.md
│       ├── impl-plan.md
│       ├── code-review.md
│       └── verification-report.md
├── page-mapping.json
├── .claude/
│   ├── agents/
│   │   ├── requirements-agent.md
│   │   ├── architecture-agent.md
│   │   ├── design-review-agent.md
│   │   ├── impl-planning-agent.md
│   │   ├── implementation-agent.md
│   │   ├── review-agent.md
│   │   ├── verification-agent.md
│   │   └── pr-agent.md
│   └── settings.json
├── skills/
│   ├── requirements-analysis/skill.md
│   ├── architecture-design/skill.md
│   ├── design-review/skill.md
│   ├── implementation-planning/skill.md
│   ├── code-review/skill.md
│   ├── test-generation/skill.md
│   ├── verification/skill.md
│   └── pr-generation/skill.md
├── hooks/
│   ├── pre-commit
│   └── pre-push
├── src/
│   ├── main/
│   │   ├── java/com/docsync/
│   │   │   ├── DocSyncApplication.java
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── client/
│   │   │   ├── model/
│   │   │   └── config/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/docsync/
│       └── sync_pipeline.py
├── tests/
│   ├── __init__.py
│   ├── unit/
│   └── integration/
├── pyproject.toml
└── README.md
```

## Coding Standards

- Follow Google Java Style Guide.
- Java 17+ features encouraged: records, sealed classes, text blocks, pattern matching.
- Maximum line length: 120 characters.
- Use `Checkstyle` for style enforcement.
- Use `SpotBugs` for static bug detection.
- No raw types — always use generics.
- No `System.out.println` — use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`).
- Prefer constructor injection over field injection (`@Autowired` on fields is forbidden).
- Throw specific exceptions; never catch `Exception` and swallow it silently.
- All public methods in service and controller classes must have Javadoc.

## Testing Standards

- All public methods in service and controller layers must have at least one unit test.
- Tests must cover: happy path, invalid input, not-found, empty data, error scenarios.
- Use **JUnit 5** as the test framework; **Mockito** for mocking.
- Use `@SpringBootTest` + `MockMvc` for controller integration tests.
- Minimum test coverage: 80% (enforced by JaCoCo Maven plugin).
- Mocks must be scoped to the layer boundary — do not mock internals of the class under test.
- Integration tests annotated with `@Tag("integration")`.
- Test classes mirror the `src/main/java` structure under `src/test/java`.

## Security Requirements

- **No secrets in source code or committed files.** All credentials must be in environment variables or MCP server env configuration.
- The `mcp.json` file contains credentials for development only; it must not be committed as-is to public repositories (ensure `.gitignore` covers it appropriately).
- All external API inputs must be validated before use.
- HTTP requests must use timeouts.
- Confluence page content must be sanitized before writing.
- GitHub webhook payloads must be verified if webhooks are implemented.

## Git Conventions

- Branch: `main` is the protected default branch.
- Feature branches: `feat/<short-description>`
- Fix branches: `fix/<short-description>`
- Commit message format: `<type>(<scope>): <description>` (Conventional Commits)
  - Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`
- Never force-push to `main`.
- Never commit `.env` files or credentials.
- Sign commits when possible.

## Documentation Conventions

- `architecture.md` lives at the **project root** — it is shared across all requirements.
- All other SDLC documents (`requirements.md`, `design-review.md`, `impl-plan.md`, `code-review.md`, `verification-report.md`) are written to `docs/<confluence-title-slug>/`.
- The `<confluence-title-slug>` is derived from the Confluence page title: lowercase, spaces/special-chars replaced with hyphens, consecutive hyphens collapsed.
  - Example: "Automated Documentation Sync" → `docs/automated-documentation-sync/`
- If no Confluence source exists, ask the human for a short project name and use that as the slug.
- These documents are the single source of truth for SDLC decisions.
- Update documents when approved changes occur — never silently.

## Build and Test Commands

```bash
# Compile
mvn compile

# Run unit tests
mvn test

# Run unit tests with coverage report
mvn test jacoco:report

# Run integration tests
mvn verify -P integration-tests

# Package (skip tests)
mvn package -DskipTests

# Full build (compile + test + package + coverage check)
mvn verify

# Run the Spring Boot app locally
mvn spring-boot:run

# Static analysis
mvn checkstyle:check
mvn spotbugs:check

# Secret scanning
detect-secrets scan --all-files
```

## Definition of Done

A task is complete only when:
- [ ] Implementation matches approved architecture and plan.
- [ ] All unit tests pass.
- [ ] Test coverage is >= 80%.
- [ ] `ruff` reports no errors.
- [ ] `mypy` reports no errors.
- [ ] No secrets are committed.
- [ ] Documentation is updated to match implementation.
- [ ] Human approval obtained for architectural decisions.

## Agent Operating Rules

All agents must:
1. Read and follow this `CLAUDE.md` before any SDLC activity.
2. Only implement what is within the approved scope.
3. Request human approval at required gates (requirements, architecture, impl-plan, PR).
4. Never modify approved documents without explicit human approval.
5. Never change the architecture silently.
6. Never remove tests to make builds pass.
7. Never disable security controls.
8. Never commit secrets.
9. Never make destructive repository changes without approval.
10. Report blockers immediately rather than working around them.

## Human Approval Requirements

Human approval is **required** before:
- Finalizing `requirements.md`
- Finalizing `architecture.md`
- Updating `architecture.md` after design review
- Finalizing `impl-plan.md`
- Creating the Pull Request
- Any destructive repository operation

## MCP Integrations

- **GitHub MCP** (`@modelcontextprotocol/server-github`): Used for reading repository content, diffs, commits, and creating PRs.
- **Confluence MCP** (`@modelcontextprotocol/server-atlassian`): Used for reading and updating Confluence pages.
- Configuration: `mcp.json` in project root (loaded by Claude Code automatically).
