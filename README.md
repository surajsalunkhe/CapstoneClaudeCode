# SDLC Agent with copilot

A Spring Boot 3.x REST API that automatically synchronises Confluence documentation with GitHub repository changes, driven by an Agentic SDLC pipeline powered by Claude Code.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [How the Agentic SDLC Pipeline Works](#2-how-the-agentic-sdlc-pipeline-works)
3. [MCP Integration — GitHub and Confluence](#3-mcp-integration--github-and-confluence)
4. [Prerequisites](#4-prerequisites)
5. [Repository Structure](#5-repository-structure)
6. [Configuration](#6-configuration)
7. [Build and Run](#7-build-and-run)
8. [API Endpoints](#8-api-endpoints)
9. [Page Mapping Configuration](#9-page-mapping-configuration)
10. [How to Use — Step by Step](#10-how-to-use--step-by-step)
11. [Known Limitations](#11-known-limitations)

---

## 1. Project Overview

This service monitors a GitHub repository for code commits and automatically keeps Confluence documentation in sync. When developers push code:

1. The service fetches commit diffs from GitHub via REST API.
2. Changed files are matched to Confluence pages using a JSON mapping config.
3. A plain-language changelog entry is appended to the matching Confluence page.
4. A structured sync report is returned listing what was updated, skipped, or failed.

The entire development lifecycle — requirements, architecture, implementation, review, and PR — is driven by **Claude Code agents** using GitHub MCP and Confluence MCP integrations.

---

## 2. How the Agentic SDLC Pipeline Works

Claude Code orchestrates the full SDLC through specialised agents. Each agent has a defined role and writes its output to `docs/<confluence-title-slug>/`:

```
Confluence URL (user story)
        │
        ▼
Requirements Agent  ──► docs/<slug>/requirements.md
        │
        ▼  (human approval)
Architecture Agent  ──► architecture.md  (project root)
        │
        ▼  (human approval)
Design Review Agent ──► docs/<slug>/design-review.md
        │
        ▼  (human approval)
Impl Planning Agent ──► docs/<slug>/impl-plan.md
        │
        ▼  (human approval)
Implementation Agent──► src/ (Java code + tests)
        │
        ▼
Review Agent        ──► docs/<slug>/code-review.md
        │
        ▼
Verification Agent  ──► docs/<slug>/verification-report.md
        │
        ▼  (human approval)
PR Agent            ──► GitHub Pull Request
```

**Folder naming rule**: The `<slug>` is derived from the Confluence page title — lowercase, spaces replaced with hyphens.
Example: "Automated Documentation Sync" → `docs/automated-documentation-sync/`

`architecture.md` always stays at the project root (shared across all requirements).

---

## 3. MCP Integration — GitHub and Confluence

Claude Code connects to both GitHub and Confluence through **Model Context Protocol (MCP) servers** configured in `mcp.json`. These servers run as local Node.js processes and expose tools that Claude Code agents call directly — no separate REST calls from the app are needed for the SDLC workflow.

### How it works

```
Claude Code Agent
      │
      │  calls tool: mcp__github__list_commits
      │              mcp__github__get_file_contents
      ▼
GitHub MCP Server (@modelcontextprotocol/server-github)
      │  authenticates with GITHUB_PERSONAL_ACCESS_TOKEN
      ▼
GitHub REST API (api.github.com)
      │
      ▼  returns commits, diffs, file contents
Claude Code Agent

      │
      │  calls tool: mcp__confluence__get_page
      │              mcp__confluence__search_content
      ▼
Confluence MCP Server (@modelcontextprotocol/server-atlassian)
      │  authenticates with ATLASSIAN_URL + ATLASSIAN_EMAIL + ATLASSIAN_API_TOKEN
      ▼
Confluence REST API (*.atlassian.net)
      │
      ▼  returns / updates page content
Claude Code Agent
```

### MCP tools used by each agent

| Agent | GitHub MCP tools | Confluence MCP tools |
|---|---|---|
| Requirements Agent | — | `get_page`, `search_content` |
| Architecture Agent | — | — |
| Design Review Agent | — | — |
| Implementation Agent | — | — |
| Review Agent | `get_file_contents`, `list_commits` | — |
| PR Agent | `create_pull_request`, `list_commits`, `get_file_contents` | — |
| The sync service itself | GitHub REST via WebClient | Confluence REST via WebClient |

### MCP configuration (`mcp.json`)

`mcp.json` is loaded automatically by Claude Code from the project root:

```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "<your-github-pat>"
      }
    },
    "confluence": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-atlassian"],
      "env": {
        "ATLASSIAN_URL": "https://yoursite.atlassian.net",
        "ATLASSIAN_EMAIL": "you@example.com",
        "ATLASSIAN_API_TOKEN": "<your-atlassian-token>"
      }
    }
  }
}
```

> **Security**: `mcp.json` contains credentials. It is listed in `.gitignore` and must never be committed to a public repository. Replace credentials with environment variable references before sharing.

### How the Requirements Agent fetches from Confluence

When you provide a Confluence page URL, the requirements-agent does the following:

1. Calls `mcp__confluence__get_page` with the page ID extracted from the URL.
2. Reads the page **title** — this becomes the `docs/<slug>/` folder name.
3. Reads the page **body** — this is the user story content.
4. Optionally calls `mcp__confluence__search_content` to find related background pages.
5. Produces `docs/<slug>/requirements.md` from the extracted content.

**Example**: Give the agent `https://sssalunkhe.atlassian.net/wiki/spaces/PROJ/pages/123456` and it will:
- Fetch page `123456` from Confluence.
- Read title e.g. `"Payment Gateway Integration"`.
- Write all SDLC docs to `docs/payment-gateway-integration/`.

---

## 4. Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ (for MCP servers) |
| Claude Code CLI | latest |
| GitHub Personal Access Token | `repo:read` scope |
| Atlassian Cloud API Token | read + write on Confluence content |

Install Claude Code:
```bash
npm install -g @anthropic-ai/claude-code
```

---

## 5. Repository Structure

```
project-root/
├── CLAUDE.md                        # Agent operating rules
├── architecture.md                  # System architecture (shared, always at root)
├── mcp.json                         # MCP server configuration (do not commit credentials)
├── page-mapping.json                # File-to-Confluence-page mapping
├── checkstyle.xml                   # Google Java Style enforcement
│
├── docs/
│   └── <confluence-title-slug>/     # One folder per requirement, named from Confluence title
│       ├── requirements.md
│       ├── design-review.md
│       ├── impl-plan.md
│       ├── code-review.md
│       └── verification-report.md
│
├── .claude/
│   ├── agents/                      # Specialised Claude Code agents
│   │   ├── requirements-agent.md
│   │   ├── architecture-agent.md
│   │   ├── design-review-agent.md
│   │   ├── impl-planning-agent.md
│   │   ├── implementation-agent.md
│   │   ├── review-agent.md
│   │   ├── verification-agent.md
│   │   └── pr-agent.md
│   └── settings.json                # Tool permissions and hooks
│
├── skills/                          # Reusable SDLC skills
│   ├── requirements-analysis/skill.md
│   ├── architecture-design/skill.md
│   ├── design-review/skill.md
│   ├── implementation-planning/skill.md
│   ├── code-review/skill.md
│   ├── test-generation/skill.md
│   ├── verification/skill.md
│   └── pr-generation/skill.md
│
├── hooks/
│   ├── pre-commit                   # Secret scanning + formatting check
│   ├── pre-push                     # Test run gate before push
│   └── pre-tool-check.sh            # Claude Code PreToolUse safety hook
│
└── src/
    ├── main/java/com/docsync/
    │   ├── DocSyncApplication.java
    │   ├── controller/              # SyncController, GlobalExceptionHandler
    │   ├── service/                 # SyncService, DiffAnalyzer
    │   ├── client/                  # GitHubClient, ConfluenceClient
    │   ├── model/                   # Java 17 records: Commit, Diff, SyncReport, etc.
    │   └── config/                  # AppConfig, MappingConfigLoader
    └── test/java/com/docsync/
```

---

## 6. Configuration

### Environment Variables (required at runtime)

| Variable | Description |
|---|---|
| `GITHUB_TOKEN` | GitHub personal access token (`repo:read`) |
| `ATLASSIAN_URL` | Confluence base URL e.g. `https://yoursite.atlassian.net` |
| `ATLASSIAN_EMAIL` | Atlassian account email |
| `ATLASSIAN_API_TOKEN` | Atlassian API token |

### `application.yml` (non-secret defaults)

```yaml
spring:
  application:
    name: doc-sync

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

docsync:
  github:
    token: ${GITHUB_TOKEN}
    base-url: https://api.github.com
    connect-timeout-seconds: 5
    response-timeout-seconds: 30
  atlassian:
    url: ${ATLASSIAN_URL}
    email: ${ATLASSIAN_EMAIL}
    api-token: ${ATLASSIAN_API_TOKEN}
    connect-timeout-seconds: 5
    response-timeout-seconds: 30
  mapping:
    default-path: page-mapping.json
```

---

## 7. Build and Run

```bash
# Compile
mvn compile

# Unit tests
mvn test

# Unit tests with JaCoCo coverage report
mvn test jacoco:report

# Full build including coverage gate (≥80%)
mvn verify

# Integration tests (requires live credentials)
mvn verify -P integration-tests

# Static analysis
mvn checkstyle:check
mvn spotbugs:check

# Package as executable JAR
mvn package -DskipTests

# Run the application
export GITHUB_TOKEN=your_github_token
export ATLASSIAN_URL=https://yoursite.atlassian.net
export ATLASSIAN_EMAIL=you@example.com
export ATLASSIAN_API_TOKEN=your_atlassian_token

mvn spring-boot:run
# or
java -jar target/doc-sync-*.jar
```

The API starts on `http://localhost:8080`.

---

## 8. API Endpoints

### `POST /api/v1/sync` — Trigger sync

```bash
curl -X POST http://localhost:8080/api/v1/sync \
  -H "Content-Type: application/json" \
  -d '{
    "repo": "owner/repo",
    "branch": "main",
    "since": "abc1234",
    "mappingPath": "page-mapping.json"
  }'
```

**Response `200 OK`**:
```json
{
  "commitsProcessed": 5,
  "pagesUpdated": 3,
  "pagesSkipped": 2,
  "errors": [],
  "dryRun": false
}
```

**Response `400 Bad Request`** (validation error):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "repo must not be blank"
}
```

### `POST /api/v1/sync/dry-run` — Simulate without writing

Same request body as `/api/v1/sync`. No Confluence pages are modified.

```bash
curl -X POST http://localhost:8080/api/v1/sync/dry-run \
  -H "Content-Type: application/json" \
  -d '{"repo": "owner/repo", "branch": "main", "since": "abc1234"}'
```

Response is identical but includes `"dryRun": true`.

### `GET /api/v1/sync/status` — Health and last run

```bash
curl http://localhost:8080/api/v1/sync/status
```

```json
{
  "status": "UP",
  "lastRunAt": "2026-08-14T10:35:00Z",
  "lastRunResult": "PASS"
}
```

### `GET /actuator/health` — Spring Boot health

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 9. Page Mapping Configuration

`page-mapping.json` maps source file glob patterns to Confluence page IDs. The service reads this file at runtime to determine which Confluence page to update for each changed file.

```json
{
  "mappings": [
    { "pattern": "src/main/java/com/docsync/client/**", "pageId": "12345678" },
    { "pattern": "src/main/java/com/docsync/service/**", "pageId": "87654321" },
    { "pattern": "src/main/java/com/docsync/controller/**", "pageId": "11112222" },
    { "pattern": "README.md", "pageId": "11111111" }
  ]
}
```

**Rules**:
- Patterns use Java glob syntax — `**` matches any depth.
- Patterns are evaluated in order — first match wins.
- `pageId` is the numeric Confluence page ID (visible in the page URL).
- Files with no matching pattern are logged and skipped — they do not cause a failure.
- One commit can update multiple pages if it touches files from different pattern groups.

**Finding your Confluence page ID**:
Open a Confluence page → look at the URL:
`https://yoursite.atlassian.net/wiki/spaces/PROJ/pages/123456789/Page+Title`
The number `123456789` is the page ID.

---

## 10. How to Use — Step by Step

### Option A — Full Agentic SDLC from a Confluence User Story

This is the primary intended workflow. Claude Code drives every stage.

**Step 1 — Open Claude Code in the project root**
```bash
cd /path/to/CapstoneClaudeCode
claude
```

**Step 2 — Provide the Confluence user story URL**
```
analyze requirements for https://yoursite.atlassian.net/wiki/spaces/PROJ/pages/123456
```

The requirements-agent will:
- Fetch the page from Confluence via MCP.
- Extract the page title to name the output folder.
- Produce `docs/<title-slug>/requirements.md` with FRs, NFRs, ACs, and open questions.
- Ask you clarification questions before finalising.
- Wait for your approval.

**Step 3 — Approve requirements, trigger architecture design**
```
approved — design the architecture
```

The architecture-agent produces `architecture.md` at the project root. Review and approve.

**Step 4 — Trigger design review**
```
review the design
```

The design-review-agent produces `docs/<slug>/design-review.md`. CRITICAL/HIGH findings must be resolved before continuing.

**Step 5 — Trigger implementation planning**
```
create implementation plan
```

The impl-planning-agent produces `docs/<slug>/impl-plan.md` with dependency-ordered tasks. Review and approve.

**Step 6 — Trigger implementation**
```
implement the approved plan
```

The implementation-agent writes Java source and tests task by task, running `mvn test` after each task.

**Step 7 — Trigger code review**
```
review the code
```

The review-agent produces `docs/<slug>/code-review.md`. Fix CRITICAL/HIGH findings.

**Step 8 — Trigger verification**
```
run verification
```

The verification-agent runs `mvn verify`, Checkstyle, SpotBugs, secret scanning, and validates all docs. Produces `docs/<slug>/verification-report.md`.

**Step 9 — Create the Pull Request**
```
create PR
```

The PR-agent checks everything, asks for your approval, then calls the GitHub MCP to create the PR with a full description.

---

### Option B — Use the REST API directly (post-implementation)

Once the application is running, call it from a GitHub Actions workflow:

```yaml
# .github/workflows/sync-docs.yml
name: Sync Docs to Confluence

on:
  push:
    branches: [main]

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - name: Trigger doc sync
        run: |
          curl -X POST https://your-deployed-host/api/v1/sync \
            -H "Content-Type: application/json" \
            -d '{
              "repo": "${{ github.repository }}",
              "branch": "main",
              "since": "${{ github.event.before }}",
              "mappingPath": "page-mapping.json"
            }'
```

Or from the command line after a push:
```bash
curl -X POST http://localhost:8080/api/v1/sync \
  -H "Content-Type: application/json" \
  -d '{
    "repo": "myorg/myrepo",
    "branch": "main",
    "since": "abc1234def5678",
    "mappingPath": "page-mapping.json"
  }'
```

---

### Option C — Dry run first, then sync

Always safe to run first to preview what would be updated:
```bash
# Preview — no Confluence writes
curl -X POST http://localhost:8080/api/v1/sync/dry-run \
  -H "Content-Type: application/json" \
  -d '{"repo": "owner/repo", "branch": "main", "since": "abc1234"}'

# If output looks correct — run the real sync
curl -X POST http://localhost:8080/api/v1/sync \
  -H "Content-Type: application/json" \
  -d '{"repo": "owner/repo", "branch": "main", "since": "abc1234"}'
```

---

## 11. Known Limitations

| Limitation | Detail |
|---|---|
| No page creation | Only updates existing Confluence pages. Unmapped files are logged and skipped. |
| Pull-based only | No webhook receiver. Trigger manually or via GitHub Actions. |
| Rule-based summaries | Diff summaries use file names and line counts — not AI-generated. Truncated at 500 chars. |
| Rate limit handling | Single retry on GitHub 429 with 2-second wait. No exponential backoff. |
| Sequential processing | Commits are processed one at a time per request. Suitable for ≤50 commits × 10 files within 5 minutes. |
| Single Confluence space | Page IDs in `page-mapping.json` must be globally unique. Multi-space requires separate mapping files. |
| Idempotency method | Duplicate detection uses a sentinel string `SHA: <sha7>` in the page body — not a database. |
