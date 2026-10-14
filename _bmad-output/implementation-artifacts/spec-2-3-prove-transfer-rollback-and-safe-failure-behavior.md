---
title: 'Story 2.3: Prove Transfer Rollback and Safe Failure Behavior'
type: 'feature'
created: '2026-10-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '1108fd314ac0b538b3dfcce56b32d315094c0b35'
context:
  - '_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 2.2 established the Transfer transaction boundary, but it does not yet prove that failures occurring after Wallet mutation leave no durable partial money movement or Transaction record.

**Approach:** Add PostgreSQL-backed integration evidence for a late domain failure, a deterministic test-only forced failure, and a genuine Transaction insert failure, all invoked through the Spring-proxied `TransferService`.

## Boundaries & Constraints

**Always:** Assert the triggering unchecked failure escapes rather than being caught or suppressed; query PostgreSQL after the proxy returns to prove exact Sender and Recipient balances equal their pre-call values and zero Transaction rows exist. Keep failure injection in test code, clean temporary database objects before and after each test, use guarded `paynest_test`, and verify captured logs reveal no email, amount, trigger message, SQL, SQLSTATE, exception class, or stack trace. Existing defined business-rejection tests must remain green.

**Never:** Add production failure switches, a Transfer entity/status/table, endpoint/controller, public error mapping, funding API, migration, helper interface/service, H2/container tooling, pessimistic locking, retry, concurrency test, or distributed mechanism. “Safe failure” here means atomic durable state plus propagated service failure; client-visible `TRANSACTION_FAILED` handling belongs to Story 2.4.

## I/O & Edge-Case Matrix

| Scenario | Input / Failure Point | Expected Durable State | Failure Behavior |
|----------|-----------------------|------------------------|------------------|
| Late domain failure | Recipient at maximum balance; credit overflows after Sender debit | Both original balances; zero Transactions | Unchecked domain failure propagates |
| Forced mid-operation failure | Test-only Clock throws after both debit and credit, before Transaction creation | Both original balances; zero Transactions | Same forced failure propagates |
| Persistence failure | Test-only PostgreSQL trigger rejects Transaction insert after both balance mutations | Both original balances; zero Transactions | Translated unchecked persistence failure propagates; logs stay safe |
| Defined business rejection | Existing invalid/unknown/self/missing-wallet/insufficient cases | No state change; zero Transactions | Existing typed failure behavior remains unchanged |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/transfer/application/TransferService.java` — preserve the public `@Transactional` boundary and uncaught exception flow; production changes are not expected unless tests expose a defect.
- `src/main/java/com/paynest/wallet/domain/Wallet.java` — `debit` precedes `credit`; recipient overflow provides a real post-debit domain failure without a test hook.
- `src/main/java/com/paynest/transaction/domain/Transaction.java` — injected `Clock` is consulted after both Wallet mutations and before repository save, providing a test-only forced-failure seam.
- `src/test/java/com/paynest/transfer/TransferServiceIntegrationTests.java` — existing real-proxy success and defined business-rejection evidence must remain unchanged and passing.
- `src/test/java/com/paynest/auth/RegistrationHttpIntegrationTests.java` — reuse its guarded temporary PostgreSQL trigger/function lifecycle and sensitive-output assertions.
- `src/test/java/com/paynest/support/TestDatabaseSupport.java` — reuse exact-database guard and Transaction-first cleanup; continue funding Wallets only through test JDBC fixtures.

## Tasks & Acceptance

**Execution:**
- [x] `src/test/java/com/paynest/transfer/TransferRollbackIntegrationTests.java` — add a dedicated non-transactional Spring test using a test-replaced Clock with a normal default, guarded fixture funding, trigger cleanup, captured output, and persisted-state helpers.
- [x] `src/test/java/com/paynest/transfer/TransferRollbackIntegrationTests.java` — prove recipient overflow after Sender debit rolls back both Wallets and persists no Transaction while the unchecked domain failure escapes.
- [x] `src/test/java/com/paynest/transfer/TransferRollbackIntegrationTests.java` — make the test-only Clock throw after both mutations and prove the identical forced failure escapes with both balances and Transaction count rolled back.
- [x] `src/test/java/com/paynest/transfer/TransferRollbackIntegrationTests.java` — install a narrowly targeted temporary `BEFORE INSERT` trigger, prove a real translated persistence failure rolls back both Wallets and creates no failed/completed row, and assert captured output excludes sensitive/internal details.
- [x] `src/test/java/com/paynest/transfer/TransferServiceIntegrationTests.java` — retain all existing defined business-rejection coverage; avoid duplicating or weakening those cases.

**Acceptance Criteria:**
- Given each late failure path, when the real Spring-proxied service exits exceptionally, then PostgreSQL contains the exact pre-call balances and no Transaction row.
- Given the forced test failure, when it escapes the transaction boundary, then it is the same exception instance and no production failure hook or extra abstraction exists.
- Given the PostgreSQL insert rejection, when output and persistence are inspected, then the failure remains safe, no sensitive/internal diagnostic is emitted, and no durable `FAILED` Transaction exists.
- Given the completed story, when application mappings, entities, migrations, and dependencies are inspected, then product scope remains unchanged and concurrency/public HTTP work is still deferred.

## Implementation Notes

- Added three PostgreSQL-backed rollback integration tests that call the Spring-proxied `TransferService`; production code, schema, dependencies, and public API remain unchanged.
- Reused guarded test database cleanup and JDBC-only fixture funding. The persistence failure uses a temporary amount-targeted trigger that is removed before and after every test.
- Scoped captured-output assertions to the transfer operation so normal Spring/PostgreSQL startup diagnostics are not mistaken for application failure logging.
- Review hardening proves the exact PostgreSQL trigger failure, blank operation logging, and debit/credit/clock/save ordering without production hooks.
- Verification passed on PostgreSQL 18: focused Story 2.3 tests (29), combined transfer integration tests (10), full Maven suite (131), and package verification.

## Spec Change Log

- 2026-10-14: Implemented and verified Story 2.3 without production-scope changes.
- 2026-10-14: Applied review patches for failure-origin, operation-log, and sequencing evidence; reran the full verification matrix successfully.

## Review Triage Log

| Finding | Verdict / route | Evidence |
|---|---|---|
| BH-1: Any `DataIntegrityViolationException` can satisfy the trigger test | medium / patch | The assertion does not prove the temporary trigger caused the persistence failure; inspect the cause chain for the trigger's SQLSTATE and message. |
| BH-2: Domain and forced failures have no captured-output assertion | medium / patch | The frozen safety constraint applies to late failures; operation-scoped blank-output assertions are a direct test-only correction. |
| BH-3: SQL check recognizes only one exact lowercase statement | medium / patch | Other SQL forms can be emitted without matching the listed substring; blank operation output proves the intended no-logging behavior. |
| BH-4: SQLSTATE check misses common spellings and the numeric code | medium / patch | The substring list is incomplete; blank operation output removes representation-specific gaps. |
| BH-5: Exception/stack-trace check lists only selected class names and frames | medium / patch | Unlisted wrappers and frames can escape detection; blank operation output is the complete assertion for this no-logging path. |
| BH-6: Persistence log slice includes rollback-verification queries | low / patch | The end snapshot is taken after database assertions, contrary to the operation-scoped claim; snapshot immediately after the service returns. |
| BH-7: Trigger amount and message are duplicated in Java and DDL | low / patch | Constants and SQL literals can diverge; format the test-only DDL from the existing constants. |
| EC-1: Global cleanup/trigger changes can collide under concurrent test execution | false / reject | No Maven or JUnit configuration enables parallel tests, and JUnit's default execution is sequential, so the cited concurrent condition is not reachable in this build. |
| EC-2: Exact diagnostic substrings permit other SQL or stack-trace forms | medium / patch | Verified duplicate of BH-3/BH-5; assert the captured operation segment is blank. |
| VG-1: Tests do not prove failures occur after the expected mutations | medium / patch | Pre-verified gap: final durable state alone stays green if operation ordering moves; focused unit tests must inspect live Wallet state at each throwing seam. |
| VG-2: Log-safety test permits SQL in unrecognized forms | medium / patch | Pre-verified duplicate of BH-3; the expected operation emits no logs, so assert a blank operation segment. |

## Design Notes

Use a dedicated test context so replacing `Clock` cannot contaminate existing success tests. Default the test Clock to a fixed valid instant, override it only for the forced-failure case, and restore/reset it per test. The PostgreSQL trigger must target only the unique test amount or participant data and be dropped in both setup and teardown. Do not add service logging merely for the test; absence of sensitive output is acceptable safe logging behavior.

## Verification

**Commands:**
- `mvn.cmd -Dtest=TransferRollbackIntegrationTests test` with guarded `PAYNEST_TEST_DB_*` — expected: all late-failure rollback and log-safety tests pass against PostgreSQL.
- `mvn.cmd -Dtest=TransferServiceIntegrationTests,TransferRollbackIntegrationTests test` with guarded test environment — expected: success, defined rejection, and rollback suites pass together without context contamination.
- `mvn.cmd test` with guarded test environment — expected: complete suite passes with no failures or skips.
- `mvn.cmd verify -DskipTests` — expected: application compiles and packages successfully.
