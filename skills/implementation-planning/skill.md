---
name: implementation-planning
description: Decomposes approved architecture into a topologically-ordered implementation plan (impl-plan.md) for a Java 17 / Spring Boot / Maven project. Each task specifies component, files, MCP tool usage, test requirements, and acceptance criteria. Ends with human approval gate.
tools: Read, Write, Bash
---

# Skill: Implementation Planning

**Trigger:** `/implementation-planning` or "create implementation plan"

## Purpose
Convert approved architecture and requirements into a dependency-ordered, atomic `impl-plan.md` that the implementation-agent can execute task-by-task.

## Fixed Package Structure
```
com.docsync/
  controller/     SyncController.java
  service/        SyncService.java
  client/         GitHubMcpClient.java, ConfluenceMcpClient.java
  model/          SyncRequest.java, SyncReport.java, CommitInfo.java, PageMapping.java
  config/         AppConfig.java
  exception/      DocSyncException.java, GitHubAuthException.java,
                  ConfluenceNotFoundException.java, GlobalExceptionHandler.java
```

## MCP Tools to Reference in Tasks
Tasks that involve MCP client code must name the exact tool:
- `mcp__github__list_commits` — GitHubMcpClient fetches commit history
- `mcp__github__get_file_contents` — GitHubMcpClient reads file contents
- `mcp__confluence__get_page` — ConfluenceMcpClient reads page before updating
- `mcp__confluence__update_page` — ConfluenceMcpClient writes synced content
- `mcp__confluence__create_page` — ConfluenceMcpClient creates missing pages

## Inputs
- `docs/<title-slug>/requirements.md` (approved) — slug provided by requirements-analysis skill
- `architecture.md` (project root)
- `docs/<title-slug>/design-review.md` (completed, no open CRITICAL findings)
- `CLAUDE.md`

## Procedure

1. Read all four documents.
2. Enumerate all implementation units: config, models, exception hierarchy, MCP clients, service, controller, integration tests.
3. Build a dependency graph — identify which tasks must precede others.
4. Sort tasks topologically — no task appears before its dependencies.
5. For each task: write ID, description, component, dependencies, exact files, implementation details (including MCP tool names where applicable), test requirements, acceptance criteria.
6. Write `docs/<title-slug>/impl-plan.md`.
7. Present to human and **request explicit approval** before any implementation begins.

## Standard Phase Order
1. Maven project setup (`pom.xml`, directory structure)
2. Domain models (Java 17 records: SyncRequest, SyncReport, CommitInfo, PageMapping)
3. Configuration (`AppConfig`, `application.properties`)
4. Exception hierarchy + `GlobalExceptionHandler`
5. MCP client wrappers (`GitHubMcpClient`, `ConfluenceMcpClient`)
6. Business logic (`SyncService`)
7. REST controller (`SyncController`)
8. Integration tests

## Output
`docs/<title-slug>/impl-plan.md` with: task list, dependency graph, execution phases.

## Validation Criteria
- Tasks topologically ordered (no circular dependencies).
- Every component in `architecture.md` has at least one task.
- Every task with MCP calls names the specific MCP tool.
- Every task has explicit test requirements and measurable ACs.
- Human has explicitly approved.

## Failure Handling
- If a component cannot be decomposed unambiguously: ask the architecture agent for clarification.
- If the plan grows beyond approved scope: flag out-of-scope items and ask for approval.
