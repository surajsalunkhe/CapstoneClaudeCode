---
name: architecture-design
description: Designs a Java 17 / Spring Boot 3.x / Maven system architecture from approved requirements.md. Proposes 2-3 alternatives, selects the best, defines components, REST endpoints, data flow, and ADRs. Ends with human approval gate.
tools: Read, Write, Bash
---

# Skill: Architecture Design

**Trigger:** `/architecture-design` or "design architecture based on requirements"

## Purpose
Design the high-level system architecture for an approved set of requirements, producing `architecture.md` ready for human approval.

## Fixed Technology Stack (from CLAUDE.md)
- Language: Java 17
- Framework: Spring Boot 3.x
- Build: Maven 3.9+
- HTTP client: Spring WebClient (blocking `.block()` for MVP)
- Testing: JUnit 5, Mockito, MockMvc, MockWebServer (OkHttp)
- GitHub integration: GitHub MCP (`mcp__github__*` tools via `mcp.json`)
- Confluence integration: Atlassian MCP (`mcp__confluence__*` tools via `mcp.json`)

## MCP Integration (referenced in architecture, not called in this skill)
The architecture must describe how the implementation will call:
- `mcp__github__list_commits` — fetch recent commits for a repo
- `mcp__github__get_file_contents` — read file contents from GitHub
- `mcp__confluence__get_page` — read an existing Confluence page
- `mcp__confluence__update_page` — update a Confluence page with synced content
- `mcp__confluence__create_page` — create a new Confluence page

## Inputs
- `requirements.md` (approved)
- `CLAUDE.md`

## Procedure

1. Read `CLAUDE.md` and `requirements.md`.
2. Propose 2–3 architecture alternatives with trade-offs.
3. Select and justify the recommended architecture.
4. Define components (one responsibility each):
   - `SyncController` — REST entry point
   - `SyncService` — orchestration
   - `GitHubMcpClient` — wraps `mcp__github__*` tools
   - `ConfluenceMcpClient` — wraps `mcp__confluence__*` tools
   - `SyncReport`, `SyncRequest`, `CommitInfo`, `PageMapping` — domain models (Java records)
   - `AppConfig` — Spring configuration, WebClient beans
   - `GlobalExceptionHandler` — `@RestControllerAdvice`
5. Define all REST endpoints with request/response schemas.
6. Define end-to-end data flow including MCP tool call sequence.
7. Define security boundaries and error handling strategy.
8. Define observability (SLF4J structured logging, `/actuator/health` only).
9. Document decisions as ADRs.
10. Write `architecture.md`.
11. Present to human and **request explicit approval**.

## Output
`architecture.md` in project root with: Overview, Components, API Design, Data Flow, Technology Stack, Security, Error Handling, Observability, ADRs, Risks.

## Validation Criteria
- Every FR addressed by at least one component.
- Every NFR addressed by at least one ADR.
- MCP tool call sequence described end-to-end.
- No component has more than one primary responsibility.
- Human has explicitly approved.

## Failure Handling
- If a requirement is impossible with the given stack: flag it explicitly. Do not silently change the requirement.
- If two requirements conflict architecturally: surface the conflict to the human.
