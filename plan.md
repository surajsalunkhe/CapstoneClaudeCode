# Claude Code Capstone Project

## Agentic SDLC Use Case: Automated Documentation Sync

**Team Exercise | Claude Code**

---

# 1. Project Overview

In this capstone, build an Agentic SDLC Pipeline from scratch using **Claude Code** and its agentic development capabilities.

The complete Software Development Lifecycle should be driven through Claude Code using:

* `CLAUDE.md`
* Claude Code agents and subagents
* Skills
* Hooks
* Tool permissions
* Structured prompts
* MCP integrations where required
* Human-in-the-loop approvals
* Git and GitHub integration

The objective is to demonstrate an end-to-end agentic software development workflow starting from a user story and ending with a production-ready Pull Request.

The workflow must cover:

1. Requirements
2. Architecture
3. Design Review
4. Implementation Planning
5. Implementation
6. Code Review
7. Verification
8. Pull Request

Claude Code must not autonomously make major architectural or implementation decisions without human approval.

---

# 2. Claude Code Project Configuration

Before starting the SDLC workflow, configure the repository for Claude Code.

## 2.1 CLAUDE.md

Create a root-level `CLAUDE.md` file.

The file must define:

* Project purpose
* Technology stack
* Repository structure
* Coding standards
* Testing standards
* Security requirements
* Git conventions
* Documentation conventions
* Build and test commands
* Definition of Done
* Agent operating rules
* Human approval requirements

Claude Code must read and follow `CLAUDE.md` before performing SDLC activities.

---

## 2.2 Agent Responsibilities

Create specialized Claude Code agents/subagents where appropriate.

Recommended responsibilities:

### Requirements Agent

Responsible for:

* Reading the user story
* Identifying functional requirements
* Identifying non-functional requirements
* Detecting ambiguity
* Asking clarification questions
* Identifying assumptions
* Producing `requirements.md`

### Architecture Agent

Responsible for:

* Reading `requirements.md`
* Proposing system architecture
* Identifying components
* Defining component responsibilities
* Defining technology choices
* Defining data flow
* Producing `architecture.md`

### Design Review Agent

Responsible for:

* Reviewing `architecture.md`
* Identifying architectural risks
* Identifying missing requirements
* Identifying scalability concerns
* Identifying security concerns
* Identifying maintainability concerns
* Producing `design-review.md`

### Implementation Planning Agent

Responsible for:

* Reading approved requirements and architecture
* Creating dependency-ordered implementation tasks
* Identifying blocked tasks
* Identifying test requirements
* Producing `impl-plan.md`

### Implementation Agent

Responsible for:

* Implementing approved tasks
* Following `CLAUDE.md`
* Following approved architecture
* Writing tests
* Running tests
* Reporting implementation status

The implementation agent must not change the approved architecture without human approval.

### Review Agent

Responsible for:

* Reviewing the implementation
* Checking requirements compliance
* Checking security
* Checking error handling
* Checking test coverage
* Checking code quality
* Checking dependency safety
* Producing review findings

### Verification Agent

Responsible for:

* Running unit tests
* Running integration tests
* Running static analysis
* Validating generated documentation
* Validating expected application behavior
* Reporting verification results

### PR Agent

Responsible for:

* Preparing the Pull Request
* Generating the PR description
* Generating changelog information
* Generating reviewer checklist
* Summarizing test evidence

---

# 3. Step 1 — Requirements

Use Claude Code to collaboratively define and document the requirements for the provided User Story.

## Input

The User Story may come from:

* Jira
* Confluence
* Word document
* Markdown document
* API
* MCP server
* User-provided text

## Claude Code Responsibilities

Claude Code must:

1. Read the User Story.
2. Identify functional requirements.
3. Identify non-functional requirements.
4. Identify assumptions.
5. Identify missing information.
6. Ask the user clarification questions when required.
7. Wait for the user's response before finalizing ambiguous requirements.
8. Incorporate the user's answers.
9. Produce the final `requirements.md`.

## Output

Create:

```text
requirements.md
```

The document must contain:

* User Story
* Business Objective
* Functional Requirements
* Non-Functional Requirements
* Assumptions
* Constraints
* Dependencies
* Acceptance Criteria
* Open Questions
* Resolved Questions

## Human Approval

Claude Code must request human approval before considering the requirements finalized.

After approval:

* Commit `requirements.md`.
* Do not modify approved requirements without explicit approval.

---

# 4. Step 2 — Architecture

Use Claude Code to design the high-level system architecture based on the approved `requirements.md`.

## Claude Code Responsibilities

Claude Code must:

1. Read `requirements.md`.
2. Analyze the requirements.
3. Propose architecture alternatives where appropriate.
4. Recommend the most suitable architecture.
5. Identify system components.
6. Define component responsibilities.
7. Define interfaces and APIs.
8. Define data flow.
9. Identify external dependencies.
10. Identify security boundaries.
11. Identify deployment considerations.
12. Identify technology choices.

## Output

Create:

```text
architecture.md
```

The document should contain:

* Architecture Overview
* Architecture Diagram
* Components
* Component Responsibilities
* API / Interface Design
* Data Flow
* Technology Stack
* Security Considerations
* Error Handling Strategy
* Scalability Considerations
* Observability Considerations
* Deployment Considerations
* Architecture Decisions
* Risks and Trade-offs

## Human Approval

Claude Code must present the architecture recommendation to the human.

The architecture must be explicitly approved before implementation planning begins.

---

# 5. Step 3 — Design Review

Use Claude Code as a senior architecture reviewer.

The Design Review Agent must read:

```text
requirements.md
architecture.md
```

and perform a structured review.

## Review Areas

### Requirements Alignment

Verify that:

* All functional requirements are addressed.
* All non-functional requirements are addressed.
* Acceptance criteria can be implemented.

### Architecture

Verify:

* Component boundaries
* Separation of responsibilities
* Dependency direction
* API design
* Data flow
* Scalability
* Maintainability

### Security

Verify:

* Authentication
* Authorization
* Input validation
* Secret management
* Sensitive data handling
* API security

### Reliability

Verify:

* Error handling
* Retry behavior
* Failure scenarios
* Timeout handling
* External dependency failures

### Testing

Verify:

* Unit testing strategy
* Integration testing strategy
* Negative testing
* Edge cases
* Contract/API testing

### Operational Readiness

Verify:

* Logging
* Monitoring
* Metrics
* Traceability
* Deployment
* Rollback strategy

## Output

Create:

```text
design-review.md
```

The document must contain:

* Review Summary
* Findings
* Risks
* Gaps
* Recommendations
* Severity
* Required Changes
* Approved Decisions
* Open Questions

## Architecture Update

If the review identifies issues:

1. Claude Code must explain the required changes.
2. Human must approve the changes.
3. Claude Code updates `architecture.md`.
4. The updated architecture must be reviewed again if the changes are significant.

---

# 6. Step 4 — Implementation Planning

Use Claude Code to convert the approved architecture into an implementation plan.

Claude Code must read:

```text
requirements.md
architecture.md
design-review.md
```

## Responsibilities

Generate a dependency-ordered implementation plan.

Each task should contain:

* Task ID
* Description
* Component
* Dependencies
* Expected files
* Implementation details
* Test requirements
* Acceptance criteria
* Status

Tasks must be ordered so that dependent work cannot start before its dependencies are completed.

## Output

Create:

```text
impl-plan.md
```

Example:

```text
TASK-001
Create project structure

TASK-002
Implement domain model

TASK-003
Implement repository layer
Depends on: TASK-002

TASK-004
Implement service layer
Depends on: TASK-003

TASK-005
Implement REST API
Depends on: TASK-004

TASK-006
Implement integration tests
Depends on: TASK-005
```

## Human Approval

The implementation plan must be approved before implementation begins.

---

# 7. Step 5 — Implementation

Claude Code must implement the approved implementation plan.

The implementation must follow:

```text
CLAUDE.md
requirements.md
architecture.md
design-review.md
impl-plan.md
```

## Implementation Rules

Claude Code must:

1. Select the next available task.
2. Verify that its dependencies are complete.
3. Implement only approved scope.
4. Follow project coding standards.
5. Write automated tests.
6. Run relevant tests.
7. Fix implementation or test failures.
8. Update implementation status.
9. Report completed work.
10. Request human approval where required.

## Human-in-the-Loop

Claude Code must not:

* Change the architecture silently.
* Introduce unrelated features.
* Modify requirements without approval.
* Remove tests to make builds pass.
* Disable security controls to bypass failures.
* Commit secrets.
* Make destructive repository changes without approval.

---

# 8. Claude Code Skills

Where appropriate, create reusable Claude Code skills for repeated SDLC activities.

Recommended skills:

```text
skills/
├── requirements-analysis/
├── architecture-design/
├── design-review/
├── implementation-planning/
├── code-review/
├── test-generation/
├── verification/
└── pr-generation/
```

Each skill should define:

* Purpose
* Inputs
* Required context
* Procedure
* Output
* Validation criteria
* Failure handling

Skills should be reusable across projects where possible.

---

# 9. Claude Code Hooks

Use Claude Code hooks where deterministic enforcement is required.

Hooks may be used for:

* Formatting
* Static analysis
* Test execution
* Secret detection
* File validation
* Markdown validation
* Preventing unauthorized file modifications
* Preventing commits containing secrets
* Running verification before commits

Prompt instructions should not be used where deterministic enforcement is required.

For example:

```text
Prompt:
"Do not commit secrets."

Deterministic enforcement:
Pre-commit secret scanning hook.
```

The hook-based approach should be preferred for mandatory security and quality gates.

---

# 10. Step 6 — Code Review

Use Claude Code as a peer reviewer before creating the Pull Request.

The Review Agent must inspect:

* Source code
* Tests
* Configuration
* Documentation
* Dependencies
* Git changes

## Review Checklist

### Correctness

Does every component behave according to:

```text
requirements.md
```

?

### Security

Verify:

* No secrets are committed.
* User input is validated.
* Authentication and authorization are correctly implemented.
* Sensitive information is not exposed in logs or API responses.

### Error Handling

Verify:

* API failures are handled.
* Missing files are handled.
* Empty repositories are handled.
* Invalid input is handled.
* External service failures are handled.
* Exceptions are not silently swallowed.

### Test Coverage

Verify that tests cover:

* Happy path
* Invalid input
* Not Found
* Missing fields
* Empty data
* Error scenarios
* Boundary conditions

### Code Clarity

Verify:

* Function names are meaningful.
* Classes have clear responsibilities.
* Logic is easy to understand.
* Unnecessary comments are avoided.
* Complex logic is appropriately documented.

### DRY Principle

Identify:

* Duplicate code
* Repeated business logic
* Repeated validation
* Repeated API handling

Recommend shared abstractions where appropriate.

### Dependency Safety

Verify:

* Dependency versions
* Known vulnerabilities
* Outdated dependencies
* Unnecessary dependencies

## Review Output

Create or update:

```text
code-review.md
```

Each finding should contain:

```text
Finding ID
Severity
File
Line
Problem
Impact
Recommendation
Status
```

---

# 11. Step 7 — Verification

Use Claude Code to execute a comprehensive verification process.

## Code Verification

Run:

* Unit tests
* Integration tests
* API tests
* Static analysis
* Formatting checks
* Build verification
* Dependency vulnerability checks
* Secret scanning

## Documentation Verification

Validate:

```text
requirements.md
architecture.md
design-review.md
impl-plan.md
code-review.md
```

Verify that:

* Documents are internally consistent.
* Requirements match implementation.
* Architecture matches implementation.
* Test evidence exists.
* Known limitations are documented.
* No unsupported claims are present.

## Verification Output

Create:

```text
verification-report.md
```

The report must contain:

* Verification Summary
* Commands Executed
* Test Results
* Static Analysis Results
* Security Results
* Documentation Validation
* Failed Checks
* Known Limitations
* Final Status

Possible final statuses:

```text
PASS
PASS WITH LIMITATIONS
FAIL
```

Claude Code must not report `PASS` when mandatory verification checks fail.

---

# 12. Step 8 — Pull Request

Use Claude Code to prepare the Pull Request.

Claude Code should use Git/GitHub tooling or MCP integration where available.

Before creating the PR:

1. Verify Git status.
2. Review the complete diff.
3. Verify tests.
4. Verify documentation.
5. Verify no secrets are present.
6. Verify implementation matches the approved plan.
7. Ask for human approval before pushing or creating the PR if required by repository policy.

---

# 13. Pull Request Description

Claude Code must generate a PR description containing all required sections.

## Summary

Provide a 2–3 sentence overview of:

* What was built.
* Why it was built.

## Changes Made

Provide a bullet list of:

* Files added.
* Files modified.
* Files deleted.
* Reason for each significant change.

## Test Evidence

Include:

* Test commands.
* Test results.
* Build results.
* CI results when available.

## Known Limitations

Document:

* Not Found items.
* Out-of-scope items.
* Known technical limitations.
* Deferred work.

## Reviewer Checklist

The PR must contain:

```text
- [ ] Requirements implemented
- [ ] Architecture approved
- [ ] Design review completed
- [ ] Implementation plan completed
- [ ] Unit tests passing
- [ ] Integration tests passing
- [ ] Security checks completed
- [ ] Dependency checks completed
- [ ] No secrets committed
- [ ] Documentation updated
- [ ] Known limitations documented
- [ ] Final verification completed
```

---

# 14. Agentic SDLC Execution Flow

The complete Claude Code workflow should follow this sequence:

```text
User Story
    |
    v
Requirements Agent
    |
    v
requirements.md
    |
    v
Human Approval
    |
    v
Architecture Agent
    |
    v
architecture.md
    |
    v
Human Approval
    |
    v
Design Review Agent
    |
    v
design-review.md
    |
    v
Architecture Update
    |
    v
Human Approval
    |
    v
Implementation Planning Agent
    |
    v
impl-plan.md
    |
    v
Human Approval
    |
    v
Implementation Agent
    |
    v
Source Code + Tests
    |
    v
Review Agent
    |
    v
code-review.md
    |
    v
Verification Agent
    |
    v
verification-report.md
    |
    v
Human Approval
    |
    v
PR Agent
    |
    v
Pull Request
```

---

# 15. Required Repository Structure

The completed repository should contain an SDLC documentation structure similar to:

```text
project-root/
│
├── CLAUDE.md
│
├── requirements.md
├── architecture.md
├── design-review.md
├── impl-plan.md
├── code-review.md
├── verification-report.md
│
├── skills/
│   ├── requirements-analysis/
│   ├── architecture-design/
│   ├── design-review/
│   ├── implementation-planning/
│   ├── code-review/
│   ├── test-generation/
│   ├── verification/
│   └── pr-generation/
│
├── hooks/
│   ├── pre-commit
│   ├── pre-push
│   └── ...
│
├── src/
│
├── test/
│
└── README.md
```

The exact directory structure may be adapted to the project's technology stack and Claude Code configuration.

---

# 16. Definition of Done

The capstone is complete only when:

* [ ] User Story has been analyzed.
* [ ] Clarification questions have been resolved.
* [ ] `requirements.md` has been approved.
* [ ] `architecture.md` has been created and approved.
* [ ] Design review has been completed.
* [ ] `design-review.md` has been created.
* [ ] Implementation plan has been approved.
* [ ] `impl-plan.md` has been created.
* [ ] Approved implementation has been completed.
* [ ] Automated tests have been created.
* [ ] Code review has been completed.
* [ ] `code-review.md` has been created.
* [ ] Verification has been completed.
* [ ] `verification-report.md` has been created.
* [ ] Security checks have passed.
* [ ] Dependency checks have passed.
* [ ] No secrets have been committed.
* [ ] Documentation is synchronized with implementation.
* [ ] Git diff has been reviewed.
* [ ] Human approval has been obtained.
* [ ] Pull Request has been created.
* [ ] PR contains all required sections.

---

# 17. Primary Objective

The primary objective of this capstone is to demonstrate that Claude Code can act as an **agentic SDLC assistant**, not merely as a code-generation tool.

The final solution should demonstrate:

```text
Requirements
    ↓
Architecture
    ↓
Design Review
    ↓
Implementation Planning
    ↓
Implementation
    ↓
Code Review
    ↓
Verification
    ↓
Pull Request
```

with Claude Code agents, subagents, skills, hooks, tools, MCP integrations, and human-in-the-loop approvals participating at appropriate stages.

The process must be **traceable, reviewable, deterministic where required, and safe for production-oriented software development**.
