---
title: 'Story 3.1: View Paginated Transaction History'
type: 'feature'
created: '2026-10-15'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '9a9825a3d086f01e3ecd4a58ebcab0b311c905a1'
context:
  - '_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** An authenticated PayNest user cannot yet retrieve their completed sent and received transfers. The history API must disclose only transfers involving the current user's Wallet and must stay safe and predictable as history grows.

**Approach:** Add the protected `GET /api/transactions` endpoint, backed by a bounded PostgreSQL participant query with fixed ordering and a safe viewer-relative response projection. Resolve ownership exclusively from the JWT principal and the current user's Wallet.

## Boundaries & Constraints

**Always:** Use zero-based pagination (`page=0`, `size=20`; `page >= 0`; `1 <= size <= 100`), fixed `createdAt DESC, id DESC` ordering, and exact page metadata. Derive `SENT`/`RECEIVED` relative to the current Wallet and expose only transaction ID, direction, counterparty name/email, amount, `USD`, `COMPLETED`, and UTC `createdAt`. Use the existing PostgreSQL history indexes and a projection/query shape that avoids lazy-loading and N+1 reads. Return the established safe API error envelope.

**Authentication decision:** Story 3.1 preserves the existing shared `401 AUTHENTICATION_FAILED` response for missing or invalid JWTs. Aligning all protected endpoints to the PRD's `INVALID_TOKEN` code is deferred to a later security-hardening change so this story does not alter completed Epic 1 or Epic 2 contracts.

**Never:** Accept a User or Wallet owner identifier from the caller; expose Wallet IDs, credentials, entities, or relationship graphs; load all history and paginate in memory; allow caller-selected sorting; add Wallet transaction collections, persisted entities, endpoints, migration changes, mapping frameworks, caches, or microservice abstractions. Story 3.2 transaction detail and Story 4.2 OpenAPI hardening remain out of scope.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Default history | Valid JWT; participant and unrelated transfers | First 20 participant records only; deterministic order and exact metadata | N/A |
| Sent/received mapping | Current Wallet is sender or receiver | Direction and opposite participant's safe name/email are derived correctly | N/A |
| Empty/final page | No matches, out-of-range page, or partial final page | Empty/partial `content` with accurate totals and `last` | N/A |
| Invalid pagination | Negative/non-integer `page`; non-integer, zero, negative, or over-100 `size` | No history repository query is executed | `400 VALIDATION_ERROR` |
| Current Wallet missing | Authenticated User has no Wallet | No transaction data is disclosed | `404 WALLET_NOT_FOUND` |
| Missing/invalid JWT | No usable authenticated principal | Request is rejected before application logic | `401 AUTHENTICATION_FAILED` |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/transaction/api/` — new history controller and safe response DTOs.
- `src/main/java/com/paynest/transaction/application/` — new query use case and viewer-relative mapping.
- `src/main/java/com/paynest/transaction/persistence/TransactionRepository.java` — participant page projection, explicit count, and fixed ordering.
- `src/main/java/com/paynest/wallet/persistence/WalletRepository.java` — existing authenticated-user Wallet lookup.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — exact protected GET matcher and JSON authentication entry-point routing.
- `src/main/java/com/paynest/common/error/GlobalExceptionHandler.java` — safe query-parameter validation mapping.
- `src/test/java/com/paynest/transaction/` — PostgreSQL-backed HTTP, query-count, and plan evidence.
- `src/test/java/com/paynest/wallet/api/CurrentWalletHttpIntegrationTests.java` — product endpoint inventory changes from five to six.

## Tasks & Acceptance

**Execution:**
- [x] `transaction/persistence` — implement a scalar participant `Page` query with explicit count and fixed ordering so results are bounded and counterparties do not cause N+1 queries.
- [x] `transaction/application` and `transaction/api` — resolve the trusted Wallet, derive direction/counterparty, validate pagination, and return the dedicated page contract without exposing entities.
- [x] `common/security/SecurityConfig.java` and `common/api/GlobalExceptionHandler.java` — protect the exact route and map malformed/out-of-range pagination safely.
- [x] `src/test/java` — cover the matrix, ownership isolation, deterministic page boundaries, response leakage, constant query count, and representative PostgreSQL index/limit plan.

**Acceptance Criteria:**
- Given an authenticated user with mixed sent, received, and unrelated transactions, when history is requested, then only committed participant records are returned in fixed descending timestamp/ID order with correct counterparties and metadata.
- Given a full 20-record page, when history is loaded, then SQL statement count remains constant and the PostgreSQL plan demonstrates bounded retrieval using the existing participant-history indexes.
- Given any caller-supplied pagination attempt, when it is invalid, then the API returns `400 VALIDATION_ERROR` without an unbounded or history repository read.
- Given no usable JWT or no current Wallet, when history is requested, then safe `401` or `404 WALLET_NOT_FOUND` responses are returned and no private transaction data leaks.

## Implementation Notes

- Added a scalar JPQL page projection joining only the two participant names/emails needed by the response, with an explicit participant count and fixed `createdAt DESC, id DESC` ordering.
- Added the protected history controller, trusted Wallet resolution, viewer-relative response mapping, bounded pagination validation, and safe malformed-parameter handling without changing entities or migrations.
- Added guarded PostgreSQL HTTP coverage for the full edge-case matrix, exact endpoint inventory, constant SQL count, and a representative bounded plan using both existing history indexes.
- Review hardening now distinguishes absent pagination values from blank/malformed values and verifies zero-history pages, inclusive size boundaries, and exact validation field details.

## Spec Change Log

- 2026-10-15: Implemented Story 3.1. PostgreSQL execution remains pending because the required `PAYNEST_TEST_DB_*` credentials are not supplied in this environment.
- 2026-10-15: PostgreSQL credentials were supplied at verification time; the focused Story 3.1 suites and complete regression suite passed, resolving the earlier execution blocker.

## Review Triage Log

| ID | Layer finding | Verdict and evidence |
|----|---------------|----------------------|
| R1 | Blank `page`/`size` values receive annotation defaults | **medium — patch.** Spring applies `defaultValue` to empty values, so malformed caller input reaches the history query instead of the frozen `400` behavior. |
| R2 | Large valid page/size combinations exceed JPA's integer offset | **low — rejected.** `PageableUtils` does throw beyond `Integer.MAX_VALUE`, but this requires an implausibly deep page and correcting it would add a new product bound or pagination design not justified for the MVP. |
| R3 | Handwritten plan SQL is not generated from the repository JPQL | **low — rejected.** This leaves a future regression-detection gap, but the SQL mirrors the inspected production joins, predicate, order, and page limit; capturing and rebinding generated SQL would add disproportionate test machinery. |
| R4 | `enable_seqscan=off` forces index use | **false.** The test intentionally proves the two indexes can support the query shape on a small deterministic fixture; it does not claim the cost-based planner will always prefer them at every table size. |
| R5 | The plan may scan/sort more than 20 matching index entries | **false.** “Bounded” means bounded result hydration and no full-history application load; exact totals and ordered OR predicates may inspect more matching index entries. The asserted database `Limit` satisfies that meaning. |
| R6 | A Wallet with genuinely zero history is not tested | **medium — patch.** An out-of-range nonempty history does not prove zero totals/pages for a newly created Wallet, which is an ordinary user state. |
| R7 | Valid `size=1` and `size=100` boundaries are not tested | **medium — patch.** The inclusive bounds are frozen behavior and an off-by-one regression would currently pass. |
| R8 | Pagination tests do not assert `fieldErrors` | **low — patch.** The safe validation envelope deliberately identifies the offending field; exact assertions are a small direct test correction. |
| R9 | Epic context says `INVALID_TOKEN` while this story says `AUTHENTICATION_FAILED` | **false.** The human-approved frozen decision explicitly scopes the existing shared behavior to Story 3.1 and defers the epic-wide correction; the apparent difference is intentional. |
| R10 | Execution task paths use obsolete package names | **low — rejected.** The Code Map contains the correct paths and the implementation is complete; the only proposed correction edits this build's spec, which review policy rejects. |
| R11 | Production query-plan regressions evade the handwritten-plan test | **low — rejected.** Verified as a future test gap, but current JPQL and representative SQL have the same predicates/order/limit and production HTTP query-count evidence; generated-SQL plan capture is disproportionate for this MVP. |
| R12 | Inclusive page-size boundaries lack verification | **medium — patch.** Repository search confirms no successful HTTP case uses `size=1` or `size=100`. |
| R13 | Pagination field details can regress unnoticed | **low — patch.** No current assertion checks the new `fieldErrors.page`/`fieldErrors.size` values. |
| R14 | Blank pagination values are treated as defaults | **medium — patch.** Independent edge review confirms the reachable malformed-input path described in R1. |
| R15 | Page offset can exceed JPA's integer limit | **low — rejected.** Bytecode inspection confirms the limit, but the state is impractical for this learning MVP and a fix requires an unapproved upper-bound decision; same route as R2. |
| R16 | Content and count can observe different READ COMMITTED snapshots | **low — rejected.** Concurrent insertion can make page metadata momentarily shift, but snapshot-stable read pagination was not promised and raising isolation adds complexity for a rare display-only effect. |
| R17 | `totalPages` saturates beyond `Integer.MAX_VALUE` | **low — rejected.** Spring's `Page` API is integer-based, but the condition requires billions of pages and changing the public metadata model is disproportionate for this MVP. |
| R18 | Blank-input behavior contradicts the frozen invalid-pagination claim | **medium — patch.** This is the same demonstrated root cause as R1/R14 and requires one behavior fix plus coverage. |

## Verification

**Commands:**
- `mvn.cmd test` — expected: all unit and PostgreSQL integration tests pass against the dedicated `paynest_test` database.
- `git diff --check` — expected: no whitespace errors.

**Results (2026-10-15):**
- Java 25 package build with tests skipped passed, compiling all production and test sources.
- Focused non-database suite passed: 94 tests, 0 failures, errors, or skips.
- PostgreSQL-backed history and endpoint-inventory tests were attempted but could not start because `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD` are unset; no database test executed and no fallback database was used.
- PostgreSQL-focused Story 3.1 and endpoint-inventory suites passed: 12 tests, 0 failures, errors, or skips.
- Complete Java 25/PostgreSQL 18.6 suite passed: 157 tests, 0 failures, errors, or skips.
- Post-review focused history suite passed: 8 tests, 0 failures, errors, or skips.
- Post-review complete Java 25/PostgreSQL 18.6 suite passed: 159 tests, 0 failures, errors, or skips.
