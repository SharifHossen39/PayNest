---
title: 'Story 4.3: Document and Verify Direct Local Startup'
type: 'feature'
created: '2026-10-19'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '6a1e9f4fc5a6cbd882a8b16de0f1af8f865d4764'
context:
  - _bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest has working APIs but no discoverable local setup guide or manual funding helper, so a learner cannot readily reproduce startup and demonstrate a transfer between initially empty wallets.

**Approach:** Add a README, a practical local-development runbook, a manual SQL funding fixture, and a focused integration test that starts the application against freshly migrated PostgreSQL and exercises the documented HTTP journey.

## Boundaries & Constraints

**Always:** Document Java 25, Maven Wrapper 3.10.0, PostgreSQL 18.6, separate databases/roles, nine existing environment variables, PowerShell-compatible JWT generation, startup, tests, OpenAPI, smoke requests, and shutdown. Explain that `.env` is not loaded automatically. Manual local/test funding credits exactly one wallet with a positive precise USD amount, preserves unrelated data, and creates no Transaction. Commit placeholders/invented accounts only.

**Never:** Add product endpoints, entities, runtime business changes, dependencies, fixture migrations, containers, or distributed infrastructure. Do not reset existing development data. Before automated reset, cleanup, or bootstrap, verify the actual PostgreSQL connection is exactly `paynest_test`; refuse otherwise. Report test-database fresh-start proof separately from any observed development-database startup.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Fresh startup | Guarded test connection and absent application schema | V1–V3 migrate, Hibernate validates, embedded HTTP server starts | Fail clearly on prerequisites; never reset another database |
| Registration/login | Two invented accounts | Signup returns 201 with one zero USD wallet each; signin returns 200 and usable JWTs | Existing safe contracts remain unchanged |
| Manual funding | Existing sender, 100.00 USD | Only sender gains 100.00; transaction count unchanged | Wrong database, missing wallet, invalid precision/amount or overflow produces no partial writes |
| Complete journey | Transfer 25.50 from funded sender | 201 COMPLETED; balances 74.50/25.50; both histories and participant details contain the same transaction | Existing ownership/security rules apply |
| Documentation/shutdown | Public docs and completed smoke session | OpenAPI/Swagger accessible; application context/server closes | Do not leave verification processes running |

</frozen-after-approval>

## Code Map

- New `README.md`, `docs/local-development.md`, and `scripts/dev/fund-wallet.sql` — currently absent delivery artifacts.
- `.env.example`, `src/main/resources/application*.yml`, `src/test/resources/application-test.yml` — retain existing configuration names/defaults.
- `mvnw*`, `.mvn/wrapper/maven-wrapper.properties`, `pom.xml` — existing versions/build; no dependency changes.
- `src/test/java/com/paynest/support/TestDatabaseSupport.java` — reuse actual-database guard; existing method-level guards occur after Spring bootstrap.
- `src/test/java/com/paynest/MigrationContractTests.java`, `PayNestApplicationTests.java` — retain migration/schema and inventory coverage.
- New `src/test/java/com/paynest/LocalStartupSmokeIntegrationTests.java` — fresh-schema startup, real HTTP journey, delivered SQL fixture, and reliable server closure.

## Tasks & Acceptance

**Execution:**
- [x] `scripts/dev/fund-wallet.sql` — add a plain SQL template with editable normalized email/amount, a local/test database guard, and atomic additive funding; do not auto-run it.
- [x] `docs/local-development.md` — provide PowerShell-first setup and Unix equivalents, prompted PostgreSQL credentials/ownership, environment exports, random JWT generation, Wrapper/profile commands, startup confirmation, docs URLs, and Ctrl+C shutdown.
- [x] `docs/local-development.md` — document both users and all seven API requests, funding, expected results, repeat-run accounts, isolated tests, and troubleshooting for JDK/Maven, JWT, PostgreSQL authentication/permissions, and ports.
- [x] `README.md` — introduce prerequisites and link the complete runbook.
- [x] `src/test/java/com/paynest/LocalStartupSmokeIntegrationTests.java` — verify matrix success/failure cases using real PostgreSQL/HTTP and the delivered SQL fixture; guard before bootstrap/reset and close server resources.
- [x] Existing suites — run regression verification, preserving contracts, migrations, and inventories.

**Acceptance Criteria:**
- Given a learner with the documented tools and an empty development database, when the runbook is followed, then the application can migrate/validate and complete the seven-endpoint journey using explicit manual funding.
- Given the dedicated test database, when smoke verification runs, then fresh startup, fixture safety, exact transfer balances, participant histories/detail, public documentation, and orderly shutdown are demonstrated.
- Given a connection to any database other than `paynest_test`, when automated verification prepares destructive setup, then it refuses before schema changes or application bootstrap.
- Given final delivery, when the full suite runs, then previous behavior and inventories remain unchanged.

## Implementation Notes

- Added the README/runbook, an explicitly invoked atomic funding SQL fixture, and three startup/fixture tests. Main application code, migrations, dependencies, seven product operations, and three entities remain unchanged. Added only a test-profile Hikari connection guard to prevent migration before database refusal.
- Fresh migration and the complete HTTP journey ran with the actual `local` profile against guarded `paynest_test` on PostgreSQL 18.6. V1–V3 applied, Hibernate validated, balances/history/details matched, and the server closed. No development-database startup was attempted or claimed, and development data was not reset.
- Three independent reviews completed; accepted findings were fixed and verified, with no deferrals. Fixture tests cover edited declarations, NULL/mismatched database selection, malformed/range/nonfinite amounts, missing wallets, inclusive maximum funding, overflow, additive credits, and complete row preservation. Negative assertions check specific SQLSTATEs.
- Verification on Java 25 using Maven Wrapper 3.10.0: focused startup/safety run passed 6 tests; final `.\mvnw.cmd clean verify` passed **191 tests, zero failures/errors/skips**, and produced the executable JAR. `git diff --check` passed. All 14 PowerShell runbook blocks parsed; the documented JWT recipe generated 32 decoded bytes without displaying its secret.
- Matrix audit: `freshTestDatabaseStartsAndCompletesTheDocumentedJourneyAndClosesItsServer` covers migration, registration/login, manual funding, complete journey, public documentation, and shutdown; `refusesAnotherDatabaseNameBeforeResetOrApplicationBootstrap` covers early refusal; `fixtureReplacesEachDeclarationEvenAfterItsEditableValuesChange` covers the editable-fixture workflow. These and the existing database guard/inventory/security suites all ran and passed.

## Spec Change Log

## Review Triage Log

| Finding | Verdict / route | Evidence |
|---|---|---|
| Blind: editing fixture declarations breaks smoke replacement | medium / patch | The runbook explicitly instructs editing the source fixture, but the test replaces only original literals; match declarations independently of their current values. |
| Blind: NULL expected database bypasses exact-match guard | low / patch | SQL three-valued comparison permits a NULL declaration; `IS DISTINCT FROM` is a direct correction. |
| Blind: manual fixture can wait indefinitely on a wallet lock | low / reject | Standalone psql does not inherit application lock settings, but no fixture timeout is promised; ordinary transfers finish within their short operation boundary. Adding independent timeout configuration/testing is unnecessary for this manual learning fixture. |
| Blind: local startup profile is unverified | medium / patch | Current smoke pins datasource settings under the test profile only; use the local profile against the guarded disposable database with an in-memory test signing secret. |
| Blind: preservation checks compare user/transaction counts only | medium / patch | Updates to existing rows would escape count assertions; compare complete fixture-owned rows alongside wallet snapshots. |
| Blind: any data-access failure satisfies rejection assertions | medium / patch | Syntax or unrelated SQL errors can incorrectly pass negative cases; assert distinguishing PostgreSQL SQLSTATEs. |
| Blind: seven paths is not seven operations | false / reject | The existing generated OpenAPI suite checks all seven exact method/path pairs and rejects additional methods; it passed in the full build and need not be duplicated in the startup journey. |
| Verification: inclusive maximum funding lacks a success test | medium / patch | The maximum currently appears only in an overflow case; add a zero-wallet maximum success followed by one-penny overflow rejection. |
| Edge: NULL expected database bypass | low / patch | Independently confirms the verified SQL NULL behavior; same direct predicate correction applies. |
| Edge: full verification can migrate before method-level guards | high / patch | Existing Spring test contexts initialize Flyway before their method guards. The new delivery procedure must enforce the promised refusal on opening test connections, before migration. Add a test-profile connection-initialization guard and verify it. |
| Manual audit: YAML documentation URL is absent | low / patch | The public YAML route exists and is tested; add its discoverable runbook link alongside JSON and Swagger UI. |

## Design Notes

Verify the JDBC target before booting a fresh Spring application on an available port. Exercise the local profile with pinned disposable datasource settings and an in-memory signing secret; ordinary test contexts retain the test profile with its connection-initialization database guard. Use Java HTTP support and close the context reliably. Guarded resets run sequentially.

## Verification

**Commands:**
- `.\mvnw.cmd '-Dtest=LocalStartupSmokeIntegrationTests,TestDatabaseSupportTests' test` with dedicated test variables — smoke, fixture, and safety cases pass.
- `.\mvnw.cmd clean verify` with dedicated test variables — full PostgreSQL suite and executable JAR build pass.
- `git diff --check` — no whitespace errors.

**Manual checks:** Read the runbook in order against actual Wrapper/configuration/DTO files; confirm examples use placeholders and that no verification process remains running.
