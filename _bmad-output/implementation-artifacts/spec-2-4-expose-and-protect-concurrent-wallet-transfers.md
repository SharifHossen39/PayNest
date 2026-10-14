---
title: 'Story 2.4: Expose and Protect Concurrent Wallet Transfers'
type: 'feature'
created: '2026-10-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ce8b97f35f87a51b46f17cd34af9c48a776a760e'
context:
  - '_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The atomic Transfer use case still reads Wallets without row locks and has no public route, so simultaneous requests can overspend or lose updates and clients cannot safely send money.

**Approach:** Load and pessimistically lock both Wallet rows in deterministic Wallet-ID order inside the existing local transaction, bound lock waits, expose the authenticated Transfer endpoint only after locking is active, and prove the final behavior with PostgreSQL concurrency and HTTP tests.

## Boundaries & Constraints

**Always:** Keep the existing monolith, PostgreSQL database, and `TransferService` transaction boundary at `READ COMMITTED`. Derive Sender only from `AuthenticatedUser`. Lock both Wallets with JPA `PESSIMISTIC_WRITE` in ascending Wallet-ID order, map roles by ID, recheck Wallet state under lock, and hold locks through commit/rollback. Externalize `PAYNEST_DB_LOCK_TIMEOUT` with a five-second local/test default and no retry. Return the PRD's nested `201` body. Preserve `401 AUTHENTICATION_FAILED`; use `400` validation, `404` missing User/Wallet, `422` self/insufficient, `409 CONFLICT` for SQLSTATE `40P01`/`55P03`, and `500 TRANSACTION_FAILED` for other Transfer persistence failures. Every failure is safe and atomic.

**Never:** Add another endpoint, entity, migration, funding path, retry, version field, JVM/distributed lock, broker, saga, or microservice infrastructure. Never expose Wallet IDs, entity graphs, SQL/driver details, exceptions, stack traces, tokens, or secrets.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Success | Valid JWT, recipient, funded USD Wallet | Nested `201`; one debit, credit, and `COMPLETED` record | — |
| Overspend race | Two Transfers exceed one balance together | One commit; exact non-negative balances; one record | Loser gets insufficient balance |
| Opposite direction | A→B and B→A start together | Same ascending lock order; both commit consistently | No exercised deadlock |
| Lock conflict | Held Wallet exceeds timeout or SQLSTATE `40P01` | Bounded rollback; unchanged state; no retry | Safe `409 CONFLICT` |
| Other rejection/failure | Invalid request/business rule or Transfer write failure | Full rollback; no record | Stable safe `4xx` or `500 TRANSACTION_FAILED` |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/wallet/persistence/WalletRepository.java` — add one ordered bulk `PESSIMISTIC_WRITE` query; retain existing reads.
- `src/main/java/com/paynest/transfer/application/TransferService.java` — consume the locked result, map roles by ID, and recheck state within the existing transaction.
- `src/main/java/com/paynest/transfer/api/{TransferController,TransferResponse}.java` — add exact POST mapping and nested PRD response; accept no Sender/Wallet ID.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java`, `src/main/java/com/paynest/common/error/GlobalExceptionHandler.java` — authenticate the exact route and add safe Transfer/SQLSTATE mappings.
- `src/main/resources/application.yml` — externalize session `lock_timeout`.
- `src/test/java/com/paynest/transfer/application/TransferServiceTests.java` — pin the ordered query, ID role mapping, and under-lock checks.
- `src/test/java/com/paynest/transfer/{TransferConcurrencyIntegrationTests,TransferHttpIntegrationTests}.java` — prove concurrency and HTTP contracts on guarded PostgreSQL.
- `src/test/java/com/paynest/wallet/CurrentWalletHttpIntegrationTests.java` — update route denial assertions and endpoint inventory.

## Tasks & Acceptance

**Execution:**
- [x] `WalletRepository.java`, `TransferService.java`, `TransferResponse.java` — implement ordered locking and the final response without new service abstractions.
- [x] `TransferController.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `application.yml` — expose only the protected endpoint with bounded waits and safe errors.
- [x] `TransferServiceTests.java` — prove one ordered query, ID-based roles, and under-lock revalidation.
- [x] `TransferConcurrencyIntegrationTests.java` — prove overspend prevention, opposite-direction completion, timeout/no retry, rollback, configuration, and database balance protection.
- [x] `TransferHttpIntegrationTests.java`, `CurrentWalletHttpIntegrationTests.java` — prove HTTP contracts, trusted Sender, safe failure, and five-endpoint inventory.

**Acceptance Criteria:**
- Given competing overspend requests on separate connections, when both finish, then one commits with exact balances and one Transaction.
- Given opposite-direction Transfers, when both start together, then both complete without lost updates.
- Given a held Wallet lock, when timeout expires, then bounded `409 CONFLICT` occurs without retry or state change.
- Given a valid JWT, when a valid Transfer is posted, then only its subject is debited and the exact nested `201` contract is returned.
- Given unexpected persistence failure, when it crosses the proxy, then rollback precedes safe `500 TRANSACTION_FAILED` without internal diagnostics.

## Implementation Notes

- Added one ordered `PESSIMISTIC_WRITE` Wallet query and kept `TransferService` as the single local `READ_COMMITTED` transaction boundary.
- Exposed only `POST /api/wallet/transfers`, deriving the Sender exclusively from the authenticated JWT subject and returning the nested PRD response.
- Added bounded PostgreSQL session lock waits plus safe transfer-specific `400`, `404`, `422`, `409`, and `500` responses without retries or new domain/infrastructure scope.
- Added real PostgreSQL HTTP, rollback, row-lock, overspend, opposite-direction, database-constraint, and endpoint-inventory coverage.
- During full regression verification, restored the pre-existing current-Wallet missing-state `500 INTERNAL_ERROR` contract; the transfer use case retains its required `404 WALLET_NOT_FOUND` response.
- Review hardening added bounded test execution, direct database proof of ascending lock acquisition, nested `40P01` coverage, and positive canonical lock-timeout configuration with a non-default two-connection test.
- Final verification on disposable PostgreSQL 18: focused Story 2.4/review suite `46/46`, complete suite `151/151`, `mvn.cmd verify -DskipTests`, and `git diff --check` all passed.

## Spec Change Log

## Review Triage Log

| ID | Finding | Verdict and evidence | Route |
|---|---|---|---|
| B1 | Transfer validation is not repeated after locking. | `false` — the immutable canonical amount and resolved party identities cannot change while waiting; the mutable Wallet currency and balance are both checked after the ordered lock query. Locking a self-transfer is intentionally avoided. | reject |
| B2 | Non-`DataAccessException` failures violate `TRANSACTION_FAILED`. | `false` — the frozen contract assigns `TRANSACTION_FAILED` to persistence failures, which Spring translates to `DataAccessException`; domain/clock failures are unexpected internal failures. Existing rollback tests prove overflow, clock, and insert failures remain atomic. | reject |
| B3 | New handlers require correlatable logging. | `false` — neither the frozen intent nor the PRD requires correlation infrastructure or logging for expected business/missing-resource errors; the response and no-sensitive-diagnostics requirements are met. | reject |
| B4 | The blocking concurrency test has no test-level timeout. | `medium` — removing or breaking `lock_timeout` could hang the synchronous test instead of failing within a bounded test duration. | patch |
| B5 | The latch does not deterministically prove row-lock overlap/order. | `medium` — it synchronizes invocation but scheduling can serialize the calls, so removing `ORDER BY` could escape the integration test. | patch |
| B6 | “Every pooled session” is asserted using one connection. | `low` — Hikari applies `connection-init-sql` per physical connection, but leasing multiple simultaneous connections is a cheap stronger regression check. | patch |
| B7 | SQLSTATE `40P01` mapping lacks executable verification. | `medium` — current tests exercise `55P03` and `23514`; deleting the `40P01` branch would not fail them. | patch |
| B8 | The balance constraint test does not prove whole-transfer rollback. | `false` — `TransferRollbackIntegrationTests` already proves rollback after recipient overflow, clock failure, and rejected Transaction insertion; this test specifically proves the database constraint defense. | reject |
| B9 | Transfer HTTP validation coverage omits malformed variants. | `false` — global HTTP-handler tests cover malformed JSON and unsupported media types, parameterized Transfer service tests cover missing/invalid/zero/negative/scale inputs, and the new endpoint test covers its semantic `400` envelope. | reject |
| B10 | Tests hard-code the default rather than verify an override. | `medium` — hard-coding the placeholder to `5s` would pass existing tests, so externalization is not protected. | patch |
| E1 | A quoted/delimited timeout value can alter initialization SQL. | `false` — this is trusted process configuration rather than request input, and invalid database configuration is allowed to fail application startup; the same operator controls the database URL and credentials. | reject |
| E2 | A zero timeout disables bounded lock waits. | `medium` — PostgreSQL accepts `0` as unlimited, contradicting the bounded-wait guarantee unless configuration rejects non-positive durations. | patch |
| E3 | Non-`DataAccessException` commit failures use `INTERNAL_ERROR`. | `false` — Spring's JPA transaction manager translates persistence/commit failures into the data-access hierarchy; unrelated runtime failures correctly remain generic internal errors. | reject |
| V1 | Deadlock SQLSTATE mapping has no executable verification. | `medium` — pre-verified gap: no test supplies nested `40P01`, so the public `409 CONFLICT` branch can regress silently. | patch |
| V2 | The external timeout override is verified only at its default. | `medium` — pre-verified gap: replacing the placeholder with a literal `5s` leaves the suite green. | patch |
| V3 | Ordered-lock concurrency can pass without lock-order contention. | `medium` — pre-verified gap: the mocked repository call and start latch do not observe which row the database locks first. | patch |

## Design Notes

Resolve Users before one ordered Wallet query keyed by User IDs so no stale unlocked Wallet enters the persistence context. Classify SQLSTATE at the HTTP boundary after the exception crosses the Spring proxy. Refactor the not-yet-public response, without another abstraction, to `{transaction:{id,direction,counterparty:{name,email},amount,currency,status,createdAt},balanceAfter}`.

## Verification

**Commands:**
- `mvn.cmd -Dtest=TransferServiceTests,TransferConcurrencyIntegrationTests,TransferHttpIntegrationTests test` with guarded `PAYNEST_TEST_DB_*` — expected: unit, concurrency, timeout, HTTP, rollback, and safe-error cases pass.
- `mvn.cmd test` with guarded test environment — expected: complete suite passes with no failures or skips.
- `mvn.cmd verify -DskipTests` — expected: application compiles and packages successfully.
