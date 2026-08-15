# Skill: PR Generation

## Purpose
Prepare and create the Pull Request using GitHub MCP integration, with a complete description covering summary, changes, test evidence, known limitations, and reviewer checklist.

## Inputs
- `docs/<title-slug>/verification-report.md` (status must not be FAIL) — slug from requirements-analysis skill
- All SDLC documents under `docs/<title-slug>/` and `architecture.md` at root
- Git status and diff
- `CLAUDE.md`

## Required Context
- GitHub repository details (owner, repo name)
- Target branch (usually `main`)
- Human approval to proceed

## Procedure

1. Read `docs/<title-slug>/verification-report.md` — abort if status is `FAIL`.
2. Run `git status` — ensure no uncommitted changes (or stage them).
3. Run `git diff main...HEAD` — review all changes for unexpected content.
4. Check for secrets: `detect-secrets scan --all-files`.
5. **Ask human for approval** before pushing.
6. Stage, commit, and push the branch.
7. Generate PR description (see format below).
8. Create PR using `gh pr create` or GitHub MCP `create_pull_request`.
9. Report the PR URL to the human.

## PR Description Format

```markdown
## Summary
[2-3 sentences]

## Changes Made
### Added
- `path/file` — purpose

### Modified  
- `path/file` — what and why

### Documentation
- `docs/<title-slug>/requirements.md`, `architecture.md` (root), `docs/<title-slug>/design-review.md`, `docs/<title-slug>/impl-plan.md`, `docs/<title-slug>/code-review.md`, `docs/<title-slug>/verification-report.md`

## Test Evidence
| Suite | Result | Coverage |
|-------|--------|----------|
| Unit tests | PASS | XX% |
| Linting | PASS | — |
| Type check | PASS | — |
| Secret scan | PASS | — |

## Known Limitations
- [from verification-report.md]

## Reviewer Checklist
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

## Validation Criteria
- PR URL is returned to the human.
- All checklist items are present in the PR description.
- Verification status is not FAIL.
- Human approved before push.

## Failure Handling
- If verification is FAIL: do not create PR. Fix issues first.
- If secrets found in diff: do not push. Alert human immediately.
- If push is rejected: investigate reason — do not force-push without explicit human instruction.
