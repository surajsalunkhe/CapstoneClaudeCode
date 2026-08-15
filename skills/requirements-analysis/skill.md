---
name: requirements-analysis
description: Transforms a user story into a structured requirements.md. Reads context from Confluence via MCP if available. Produces FRs, NFRs, ACs (Given/When/Then), assumptions, constraints, and open questions. Ends with a human approval gate.
tools: Read, Write, Bash, mcp__confluence__get_page, mcp__confluence__search_content
---

# Skill: Requirements Analysis

**Trigger:** `/requirements-analysis` or "analyze requirements for <user story>"

## Purpose
Transform a user story (from any source) into a structured, unambiguous `requirements.md` document ready for human approval.

## MCP Integration
- `mcp__confluence__get_page` — retrieve user stories or background from Confluence
- `mcp__confluence__search_content` — search Confluence for related business context

## Inputs
- User story text (inline **or** Confluence page URL / page ID)
- `CLAUDE.md` (always read first)

## Output Location Rules

**`architecture.md` always stays in the project root.**

All other SDLC documents are written to `docs/<title-slug>/`:
- Derive `<title-slug>` from the Confluence page **title**: lowercase, spaces/special-chars → hyphens, collapse consecutive hyphens.
- Example: "Automated Documentation Sync" → `docs/automated-documentation-sync/`
- If input is inline (no Confluence URL), ask the human for a short project name to use as the slug.

## Procedure

1. Read `CLAUDE.md`.
2. If a Confluence URL or page ID is provided:
   a. Call `mcp__confluence__get_page` to retrieve the page.
   b. Extract the page **title** — derive `<title-slug>` from it.
   c. Use `mcp__confluence__search_content` to find related context if needed.
3. Run `mkdir -p docs/<title-slug>`.
4. Extract: business objective, actors, actions, expected outcomes.
5. Enumerate functional requirements (FR-NNN).
6. Enumerate non-functional requirements (NFR-NNN): performance, security, reliability, maintainability, scalability.
7. List assumptions (A-NNN), constraints (C-NNN), dependencies (D-NNN).
8. Write acceptance criteria in Given/When/Then format (AC-NNN).
9. List open questions (OQ-NNN) — anything ambiguous or missing.
10. Ask the human the open questions. Wait for answers.
11. Incorporate answers and move resolved questions to Resolved Questions (RQ-NNN).
12. Write `docs/<title-slug>/requirements.md`.
13. Present document to human and **request explicit approval**.
14. After approval: commit `docs/<title-slug>/requirements.md`.
15. Echo the resolved path `docs/<title-slug>/` to the human.

## Output
`docs/<title-slug>/requirements.md` with sections: User Story, Business Objective, Functional Requirements, Non-Functional Requirements, Assumptions, Constraints, Dependencies, Acceptance Criteria, Open Questions, Resolved Questions.

## Validation Criteria
- All sections present and non-empty.
- Every FR is testable.
- Every AC is measurable.
- No OPEN questions remain (or assumptions documented for each).
- Human has explicitly approved the document.

## Failure Handling
- If the user story is too vague: list what is known and ask targeted questions. Do not invent requirements.
- If MCP call fails: proceed with inline text and note the failure.
- If requirements conflict: flag the conflict explicitly. Do not resolve it silently.
