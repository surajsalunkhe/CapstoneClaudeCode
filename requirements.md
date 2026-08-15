# Requirements

## User Story

**Title**: Automated Documentation Sync

**As a** software development team,
**We want** an automated system that monitors our GitHub repository for code changes and keeps our Confluence documentation synchronized,
**So that** our documentation never drifts out of sync with the codebase, reducing the manual overhead of documentation maintenance and improving team knowledge accuracy.

**Background**: Our team frequently pushes code changes but documentation in Confluence becomes stale within days. Developers spend 2–3 hours per sprint manually updating Confluence pages. We want Claude Code with its GitHub and Confluence MCP integrations to bridge this gap.

---

## Business Objective

Eliminate documentation drift between the GitHub codebase and Confluence by automating the detection of code changes and the propagation of documentation updates to the corresponding Confluence pages. The primary success metric is reducing manual documentation effort from 2–3 hours per sprint to near zero, while ensuring Confluence pages remain accurate within one pipeline run of any commit to main.

---

## Functional Requirements

**FR-001**: The system MUST read the list of commits pushed to a specified GitHub repository branch (default: `main`) since a given reference point (last sync SHA or a specified commit range).

**FR-002**: For each commit, the system MUST retrieve the file-level diff (added, modified, deleted files and their unified diffs).

**FR-003**: The system MUST analyze the diff content to extract documentation-relevant information: what changed, in which module/component, and a human-readable summary of the change.

**FR-004**: The system MUST maintain or accept a mapping between source code paths/patterns and Confluence page identifiers (page IDs or page titles) so that changed files can be associated with the correct Confluence page.

**FR-005**: For each changed file that has a corresponding Confluence page mapping, the system MUST update the Confluence page by appending a structured change-log entry containing: commit SHA (short), commit author, commit timestamp, changed files list, and a plain-language summary of the changes.

**FR-006**: The system MUST handle the case where a changed file has no corresponding Confluence page mapping — it MUST log the unmapped file and continue processing without crashing.

**FR-007**: The system MUST handle GitHub API failures (network errors, rate-limits, invalid repository) gracefully — log the error with context and either retry or exit with a clear error message.

**FR-008**: The system MUST handle Confluence API failures (network errors, page-not-found, authentication errors) gracefully — log the error with context and continue processing other pages where possible.

**FR-009**: The system MUST support a dry-run mode where it prints the planned Confluence updates without actually writing to Confluence.

**FR-010**: The system MUST produce a structured sync report after each run, listing: commits processed, pages updated, pages skipped (no mapping), and errors encountered.

**FR-011**: The system MUST be executable as a Python CLI command: `python -m doc_sync.sync_pipeline --repo <owner/repo> --branch <branch> --since <SHA|date>`.

---

## Non-Functional Requirements

**NFR-001 — Performance**: A single pipeline run processing up to 50 commits with up to 10 changed files each MUST complete within 5 minutes under normal API response times.

**NFR-002 — Reliability**: The system MUST NOT crash on partial failures (e.g., one Confluence page update fails). It MUST continue processing remaining items and report all failures in the sync report.

**NFR-003 — Security**: No credentials (GitHub token, Confluence API token) MAY appear in source code, configuration files committed to the repository, or log output. All credentials MUST be sourced from environment variables or the MCP server environment configuration.

**NFR-004 — Security**: All HTTP calls to external APIs MUST specify explicit timeouts (connect timeout ≤ 5s, read timeout ≤ 30s).

**NFR-005 — Maintainability**: The codebase MUST follow the standards in `CLAUDE.md` (PEP 8, type hints, ruff, mypy). Cyclomatic complexity per function MUST not exceed 10.

**NFR-006 — Testability**: Unit test coverage MUST be ≥ 80% for all source modules. External API calls MUST be mockable at the client boundary.

**NFR-007 — Observability**: The system MUST produce structured log output (timestamp, level, message, context) for every significant action: commit read, diff analyzed, mapping lookup, Confluence update attempted, errors.

**NFR-008 — Portability**: The system MUST run on Python 3.11+ on macOS and Linux without OS-specific dependencies.

**NFR-009 — Idempotency**: Running the pipeline twice with the same commit range MUST NOT create duplicate entries in Confluence (the system MUST detect and skip already-synced commits using a marker or commit SHA check in the page content).

---

## Assumptions

**A-001**: The Confluence space and pages already exist. The system only updates existing pages; it does not create new ones.

**A-002**: A page-mapping configuration (JSON or YAML file) will be provided by the operator, mapping glob patterns of source file paths to Confluence page IDs.

**A-003**: The GitHub MCP server and Confluence MCP server are pre-configured and accessible via the `mcp.json` in the project root (credentials are injected by the MCP server environment, not by the application).

**A-004**: The system is triggered manually or by a scheduled job (e.g., cron or GitHub Actions workflow dispatch) rather than via a live webhook for the MVP.

**A-005**: The Confluence instance is Atlassian Cloud (not Server/Data Center). The API endpoints and authentication method (API token) are those for Cloud.

**A-006**: The GitHub repository is accessible with the configured token (read access to repository contents and commit history is sufficient).

**A-007**: Page content is updated by appending to a designated section of the Confluence page (e.g., a section titled "Recent Code Changes"), not by replacing the full page body.

---

## Constraints

**C-001**: Must use Python 3.11+ as the implementation language (per `CLAUDE.md`).

**C-002**: Must use the existing GitHub MCP (`@modelcontextprotocol/server-github`) and Atlassian MCP (`@modelcontextprotocol/server-atlassian`) servers as configured in `mcp.json` — no direct REST API calls that bypass MCP where MCP tools are available.

**C-003**: Must not create new Confluence pages — only update existing ones (MVP scope boundary).

**C-004**: Must not implement a live webhook receiver (MVP scope boundary) — pull-based trigger only.

**C-005**: Must not store credentials in any committed file.

**C-006**: The page-mapping configuration file must be human-editable (JSON or YAML format).

---

## Dependencies

**D-001**: **GitHub MCP server** (`@modelcontextprotocol/server-github`) — provides tools to list commits, retrieve diffs, and read repository content.

**D-002**: **Atlassian MCP server** (`@modelcontextprotocol/server-atlassian`) — provides tools to read and update Confluence pages.

**D-003**: **Python packages**: `pytest`, `pytest-cov`, `pytest-mock`, `ruff`, `mypy`, `detect-secrets` (all development dependencies). Runtime dependencies to be determined during implementation (may include `pyyaml` for config parsing).

**D-004**: **GitHub repository access**: Read access to the target repository's commits and content.

**D-005**: **Confluence access**: Read and write access to the target Confluence space pages.

---

## Acceptance Criteria

**AC-001**:
- **Given** a GitHub repository with 3 new commits on `main` since the last sync SHA,
- **When** the pipeline is run with `--repo owner/repo --branch main --since <last-sha>`,
- **Then** the system reads all 3 commits and their diffs without error.

**AC-002**:
- **Given** a commit that modifies `src/doc_sync/github_client.py`,
- **And** the page-mapping config maps `src/doc_sync/*` to Confluence page ID `12345`,
- **When** the pipeline processes that commit,
- **Then** Confluence page `12345` is updated with a changelog entry containing the short SHA, author, timestamp, file name, and a plain-language summary.

**AC-003**:
- **Given** a commit that modifies `README.md`,
- **And** `README.md` has no entry in the page-mapping config,
- **When** the pipeline processes that commit,
- **Then** the system logs "No Confluence mapping found for README.md" and continues without crashing.

**AC-004**:
- **Given** the GitHub API returns a 503 error for a commit diff request,
- **When** the pipeline attempts to retrieve that diff,
- **Then** the system logs the error with the commit SHA and exits with a non-zero status code (or skips with a logged warning if retry is implemented).

**AC-005**:
- **Given** the Confluence API returns a 404 for page ID `99999`,
- **When** the pipeline attempts to update that page,
- **Then** the system logs the error with page ID and commit SHA, continues processing other pages, and includes the failure in the sync report.

**AC-006**:
- **Given** the pipeline is run in `--dry-run` mode,
- **When** it would normally update a Confluence page,
- **Then** it prints the planned update content to stdout and makes NO write calls to Confluence.

**AC-007**:
- **Given** the pipeline is run twice with the same commit SHA range,
- **When** the second run processes a commit already synced (SHA present in page content),
- **Then** the system skips that commit and does not append a duplicate changelog entry.

**AC-008**:
- **Given** any run completes (success or partial failure),
- **When** the pipeline exits,
- **Then** a sync report is printed to stdout listing: commits processed count, pages updated count, pages skipped count, and errors list.

---

## Open Questions

**OQ-001**: What is the format of the page-mapping configuration? Suggested: a JSON file mapping glob patterns to Confluence page IDs. — **Status**: OPEN — Assumption A-002 applies (JSON/YAML, operator-provided).

**OQ-002**: Where in the Confluence page should the changelog entry be appended? Top of page, bottom, or a named section? — **Status**: OPEN — Assumption A-007 applies (append to a section titled "Recent Code Changes").

**OQ-003**: Should the system support multiple Confluence spaces, or a single space per run? — **Status**: OPEN — Assumption: single space per run for MVP; page IDs in the mapping are globally unique.

**OQ-004**: What retry policy should apply for transient API failures (429, 503)? — **Status**: OPEN — For MVP: single retry with 2-second backoff for GitHub; no retry for Confluence (log and continue).

---

## Resolved Questions

**RQ-001**: Is webhook-based triggering required?
- **Answer**: No. Pull-based trigger (manual CLI or scheduled job) is acceptable for MVP.
- **Resolved**: Per user story scope boundaries.

**RQ-002**: Should the system create new Confluence pages for unmapped files?
- **Answer**: No. Only update existing pages. Log unmapped files.
- **Resolved**: Per user story scope boundaries.

**RQ-003**: What Python version is required?
- **Answer**: Python 3.11+.
- **Resolved**: Per `CLAUDE.md`.

**RQ-004**: Which Confluence deployment type?
- **Answer**: Atlassian Cloud.
- **Resolved**: Per `mcp.json` ATLASSIAN_URL (`sssalunkhe.atlassian.net`).
