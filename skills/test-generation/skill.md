---
name: test-generation
description: Generates comprehensive JUnit 5 / Mockito / MockMvc / MockWebServer tests for Java 17 / Spring Boot / Maven components. Covers happy path, invalid input, not-found, empty data, error scenarios, and boundary conditions. Targets ≥80% JaCoCo coverage.
tools: Read, Write, Edit, Bash
---

# Skill: Test Generation

**Trigger:** `/test-generation` or "generate tests for <component>"

## Purpose
Write JUnit 5 test classes for a given component, covering all scenarios required by `impl-plan.md` and `requirements.md`.

## MCP Integration
- No MCP calls required for test generation
- Tests for `GitHubMcpClient` and `ConfluenceMcpClient` use `MockWebServer` to simulate MCP HTTP responses

## Inputs
- Target source files in `src/main/java/com/docsync/`
- `requirements.md`, `architecture.md`, `impl-plan.md`, `CLAUDE.md`

## Test Framework by Component

### Domain Models (Java records)
- Verify all record components accessible
- Verify `@Valid` annotations enforced by Spring Validator

### AppConfig
- `@SpringBootTest` loads context successfully
- WebClient beans non-null, timeouts configured

### Exception Hierarchy + GlobalExceptionHandler
- `@WebMvcTest` + `MockMvc` — one test per exception type
- Verify HTTP status code and error response body per type
- Verify unknown exceptions return 500

### MCP Client Wrappers (GitHubMcpClient, ConfluenceMcpClient)
Use `MockWebServer` (OkHttp) to simulate HTTP responses:
```java
MockWebServer server = new MockWebServer();
server.start();
// point WebClient at server.url("/")
```
Scenarios:
- Happy path: mock 200, verify parsed result
- 401/403: verify `GitHubAuthException` thrown
- 404: verify `ConfluenceNotFoundException` thrown
- 500: verify `DocSyncException` thrown
- Timeout: verify `DocSyncException` thrown
- Empty body: verify graceful handling

### SyncService
Use `@ExtendWith(MockitoExtension.class)` + `@Mock`:
- `sync()` happy path: verify MCP client calls, verify `SyncReport` content
- `sync()` one page fails: error recorded in `SyncReport.errors`, other pages processed
- `sync()` all pages fail: `SyncReport.errors` populated, no exception thrown
- `dryRun()` flag: no write calls to Confluence MCP, `SyncReport.dryRun=true`

### SyncController
Use `@WebMvcTest(SyncController.class)` + `MockMvc`:
- `POST /api/v1/sync` valid → 200 + SyncReport JSON
- `POST /api/v1/sync` missing field → 400
- `POST /api/v1/sync` invalid repo format → 400 + validation message
- `POST /api/v1/sync/dry-run` → 200 + SyncReport (dryRun=true)
- `GET /api/v1/sync/status` → 200 + status JSON
- Service throws `DocSyncException` → 503
- Service throws `GitHubAuthException` → 401

## Naming Convention
`methodName_scenario_expectedOutcome` — e.g. `sync_onePageFails_errorRecordedOtherPagesSynced`

## Test Pattern
```java
@ExtendWith(MockitoExtension.class)
class SyncServiceTest {
    @Mock GitHubMcpClient gitHubMcpClient;
    @Mock ConfluenceMcpClient confluenceMcpClient;
    @InjectMocks SyncService sut;

    @Test
    void sync_happyPath_returnsSyncReport() {
        // given
        given(gitHubMcpClient.listCommits(any(), any()))
            .willReturn(List.of(new CommitInfo("sha1", "feat: add endpoint", Instant.now())));
        // when
        SyncReport report = sut.sync(new SyncRequest("owner", "repo", "page-id-1", false));
        // then
        assertThat(report.synced()).isEqualTo(1);
        assertThat(report.errors()).isEmpty();
    }
}
```

## Coverage Target
- JaCoCo line coverage ≥ 80% (enforced by Maven gate in `mvn verify`)
- Every public method in service and controller has at least one test

## Validation Criteria
- All test categories covered (happy path, invalid, not-found, empty, error, boundary)
- Tests are independent — no shared mutable state
- `mvn test` passes with zero failures
- JaCoCo report shows ≥ 80% line coverage

## Failure Handling
- If a component cannot be unit-tested without integration: flag for refactoring
- Never remove or disable tests to make the build pass
