---
title: 'Story 2.2: Implement and Validate the Atomic Transfer Use Case'
type: 'feature'
created: '2026-10-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ca8f1f37f3bc76d558161f0f80ff75d3e9b9bf82'
context:
  - '_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest can persist completed Transaction records but has no application use case that safely coordinates Sender/Recipient resolution, exact balance movement, and Transaction creation.

**Approach:** Add one concrete transactional `TransferService` with service-level request/result records, focused validation and business failures, then prove successful and rejected non-concurrent Transfers against PostgreSQL before exposing any HTTP endpoint.

## Boundaries & Constraints

**Always:** Derive the Sender only from the trusted authenticated User ID; normalize and validate the Recipient email; reject invalid exact amounts, unknown Recipient, self-Transfer, missing Wallets, non-USD Wallets, and insufficient balance before commit. One application transaction must cover Sender debit, Recipient credit, and exactly one immutable `COMPLETED` Transaction insert. Return only Transaction ID, `SENT`, safe Recipient identity, exact amount, `USD`, `COMPLETED`, creation time, and Sender `balanceAfter`.

**Never:** Add an endpoint/controller, funding API or production seed, Transfer entity/table/repository, extra Transaction status, locking/concurrency behavior, helper service/interface, schema migration, entity serialization, or caught/suppressed rollback failure. Do not change V1–V3.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Successful Transfer | Trusted Sender ID; normalized registered Recipient; balances `500.00`/`100.00`; amount `150.00` | Balances become `350.00`/`250.00`; one completed record; safe exact result | N/A |
| Invalid request | Missing/malformed/overlong email; null, non-positive, over-scale, or overflowing amount | No repository mutation | Field-aware validation failure |
| Invalid parties | Unknown Recipient, same User, or required Wallet absent | No balance or Transaction change | Defined user/self/wallet failure |
| Insufficient funds | Valid parties; amount exceeds Sender balance | Both balances unchanged; no Transaction | Defined insufficient-balance failure |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/transfer/package-info.java` — existing module seam; add API/application code beneath it without a persisted Transfer model.
- `src/main/java/com/paynest/auth/security/AuthenticatedUser.java` and existing controllers — preserve the convention that the security boundary passes only `user.id()` into an application service.
- `src/main/java/com/paynest/user/domain/EmailNormalizer.java` and `user/persistence/UserRepository.java` — reuse canonical email behavior and `findById`/`findByEmail` party lookup.
- `src/main/java/com/paynest/wallet/domain/Wallet.java` and `wallet/persistence/WalletRepository.java` — Wallet retains debit/credit invariants; use unlocked `findByUserId` only at this learning stage.
- `src/main/java/com/paynest/transaction/domain/Transaction.java` and `transaction/persistence/TransactionRepository.java` — reuse `Transaction.completed(...)` and persist exactly one record.
- `src/main/java/com/paynest/auth/application/RegistrationService.java` — follow its explicit service-boundary validation, normalization, concrete-service, and transaction conventions.
- `src/test/java/com/paynest/support/TestDatabaseSupport.java` — reuse guarded cleanup; fund test Wallets only with test-source JDBC fixtures.
- `src/test/java/com/paynest/wallet/CurrentWalletHttpIntegrationTests.java` — retain its assertion that only the existing four product endpoints are currently mapped.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/com/paynest/transfer/api/TransferRequest.java` and `TransferResponse.java` — define the minimal service request and safe flat completed result without Sender/Wallet IDs or entity references.
- [x] `src/main/java/com/paynest/common/error/{InvalidTransferException,UserNotFoundException,WalletNotFoundException,SelfTransferNotAllowedException,InsufficientBalanceException}.java` — add only the meaningful unchecked failures required by Story 2.2; defer HTTP mappings.
- [x] `src/main/java/com/paynest/transfer/application/TransferService.java` — validate/canonicalize first, resolve the trusted Sender and normalized Recipient, enforce business rules, mutate both Wallets, save the completed Transaction, and map the result within one `@Transactional` operation.
- [x] `src/test/java/com/paynest/transfer/application/TransferServiceTests.java` — unit-test every request/business rejection and verify no inappropriate persistence call or balance mutation.
- [x] `src/test/java/com/paynest/transfer/TransferServiceIntegrationTests.java` — use the real Spring proxy and guarded PostgreSQL fixtures to prove committed exact balances, one durable record, exact result mapping, and unchanged rejected state.
- [x] `src/test/java/com/paynest/wallet/CurrentWalletHttpIntegrationTests.java` — preserve explicit evidence that `POST /api/wallet/transfers` is not mapped yet.

**Acceptance Criteria:**
- Given fixture-funded persisted parties, when `TransferService.transfer(senderId, request)` succeeds, then debit, credit, and one completed Transaction commit atomically and the returned fields exactly match the approved safe result.
- Given any defined validation or business rejection, when the non-concurrent use case executes, then the expected typed failure is raised, both persisted balances remain unchanged, and no Transaction exists.
- Given the completed Story 2.2 application, when Spring mappings and persistence inventories are inspected, then no Transfer endpoint or fourth persisted entity exists.

## Implementation Notes

- Added a concrete `TransferService` transaction boundary that validates before repository access, resolves the trusted Sender and normalized Recipient, loads unlocked Wallets, applies stable business failures, delegates exact arithmetic to Wallet, and saves one completed Transaction.
- Added a flat service request/result contract and typed unchecked failures without adding HTTP mappings, persistence types, migrations, or locking behavior.
- Added unit coverage for all request/business rejections and PostgreSQL coverage for exact committed state and unchanged rejected state; the existing HTTP inventory now explicitly rejects `POST /api/wallet/transfers`.
- Review hardening pinned exact-balance/maximum-amount success, the accepted 254-character email boundary, and both non-USD guard operands. Final independent verification passed 23 focused tests, 7 PostgreSQL integration tests, all 125 project tests with no failures/errors/skips, and packaging.

## Spec Change Log

- 2026-10-14: Implemented Story 2.2 and completed focused, PostgreSQL, full-suite, and packaging verification.

## Review Triage Log

| Reviewer finding | Verdict | Evidence and route |
|---|---|---|
| Blind 1 — recipient balance overflow lacks a preflight typed failure | low | A near-maximum Recipient can make `Wallet.credit` throw after the in-memory debit, but the unchecked exception exits the transactional proxy and rolls back every persistent change. The scenario requires exceptional fixture state, no error contract is defined for it, and adding one now would expand behavior rather than directly correct atomicity. |
| Blind 2 — a non-USD persisted Wallet is reported as request validation | low | PostgreSQL and the Wallet factory enforce USD, so this path is only reachable through corrupted state or reflection-based tests. It fails before mutation; a new public failure type is not justified for this unreachable production state. |
| Blind 3 — monetary maximum is duplicated | low | The service, Wallet, Transaction, and schema each enforce their own boundary at distinct layers. There is no current divergence; centralizing it would add a cross-module abstraction for hypothetical drift in a three-entity learning project. |
| Blind 4 — email validation duplicates registration behavior | low | The Transfer boundary deliberately mirrors the established concrete-service validation convention and uses the shared `EmailNormalizer`. Extraction would add abstraction without a present behavioral mismatch. |
| Blind 5 — exact-balance success is not pinned | medium | No Transfer test proves `amount == sender balance`; changing the service comparison from `>` to `>=` would pass the existing tests. Patch with an inclusive successful boundary test. |
| Blind 6 — maximum supported amount acceptance is not pinned | medium | Existing Transfer tests cover only an amount above the maximum. Combine this with the exact-balance patch using `99999999999999999.99` so service validation and result preservation are exercised. |
| Blind 7 — valid 254-character recipient email acceptance is not pinned | medium | The Transfer validator has a 254-character inclusive contract but tests only the 255-character rejection. Patch the exact accepted boundary through normalized repository lookup. |
| Blind 8 — non-USD Sender branch is untested | low | The existing synthetic non-USD test covers only the Recipient operand. Patch the focused unit test to exercise each operand and prove no mutation. |
| Blind 9 — unknown Sender lacks PostgreSQL integration coverage | false | The trusted security boundary supplies an existing User ID, and the unit test already proves a missing Sender fails before Wallet or Transaction access. Database behavior adds no distinct state transition here. |
| Blind 10 — response constants are constructible as arbitrary strings | false | `TransferService` is the sole production constructor and always emits fixed `SENT`, Wallet `USD`, and Transaction `COMPLETED` values. Introducing response-only enums would not correct an observed output and would enlarge the contract prematurely. |
| Edge 1 — claimed exhaustive business-rejection tests omit non-USD Sender | low | Confirmed as the same uncovered first operand identified by Blind 8; patch the focused unit test for both Sender and Recipient currency corruption. |
| Verification 1 — inclusive amount boundaries are not pinned | medium | Pre-verified evidence shows neither exact-balance nor exact-maximum success traverses both service comparisons. Patch with one maximum exact-balance successful unit case. |
| Verification 2 — late persistence failure rollback is not exercised | medium | The gap is real, but the approved intent limits this story's proof to successful and defined rejected non-concurrent Transfers and explicitly assigns forced mid-operation rollback proof to Story 2.3. Defer that integration test to Story 2.3. |

## Design Notes

Use one concrete `TransferService` with direct owning repositories; do not add `WalletTransferAccess` or `TransactionRecordingService`. Validate before mutation, perform an explicit insufficient-balance precheck for the stable failure, then still call `Wallet.debit` so the Wallet owns its invariant. Let unchecked failures propagate so Spring can roll back. Story 2.3 adds forced rollback proof; Story 2.4 replaces unlocked Wallet reads with ordered pessimistic locking and adds the public endpoint/error mappings.

## Verification

**Commands:**
- `mvn.cmd -Dtest=TransferServiceTests test` — expected: focused service validation and business-rule tests pass without a database.
- `mvn.cmd -Dtest=TransferServiceIntegrationTests test` with the guarded `PAYNEST_TEST_DB_*` and JWT secret environment — expected: PostgreSQL success/rejection scenarios pass.
- `mvn.cmd test` with the guarded test environment — expected: complete suite passes with no failures or skips.
- `mvn.cmd verify -DskipTests` — expected: the application compiles and packages successfully.
