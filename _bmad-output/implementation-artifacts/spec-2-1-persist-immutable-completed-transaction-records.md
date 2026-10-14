---
title: 'Story 2.1: Persist Immutable Completed Transaction Records'
type: 'feature'
created: '2026-10-14'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '4dc9e44ae383069d95ed1b21c22c3c37f50da9cd'
context:
  - '_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest can persist Users and Wallets but has no durable representation of a successful money movement, so later Transfer and history stories lack their required third domain entity.

**Approach:** Add the append-only V3 Transaction schema, an immutable completed-only Transaction mapping, and a feature-owned repository, then prove the mapping and PostgreSQL constraints with staged migration, domain, and persistence tests.

## Boundaries & Constraints

**Always:** Keep `User`, `Wallet`, and `Transaction` as the only persisted entities. Use generated `Long` identity, exact `BigDecimal`/`NUMERIC(19,2)`, injected UTC `Clock`, and required lazy unidirectional Sender/Receiver Wallet associations owned by Transaction. V3 must be append-only and must preserve V1/V2 checksums. A supported creation path always creates `COMPLETED` records with no public business-field mutation path.

**Never:** Add Transfer persistence or orchestration, an API endpoint, balance mutation, locking, extra Transaction statuses, Wallet transaction collections, cascades, orphan removal, update/delete use cases, funding APIs, H2, container tooling, or changes to V1/V2.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Valid completed record | Two persisted distinct Wallets, exact positive amount, fixed Clock | One record with preserved participants and amount, `COMPLETED`, generated ID, and microsecond UTC timestamp | N/A |
| Invalid amount | Null, zero, negative, over-scale, overflow, or PostgreSQL `NaN` | No valid Transaction is committed | Domain rejection where constructible; named DB check rejects raw invalid data |
| Invalid participants | Missing/nonexistent participant or the same Wallet on both sides | No invalid Transaction is committed | Domain validation plus named `NOT NULL`, FK, or distinct-Wallet constraint |
| Unsupported state | Raw insert with status other than `COMPLETED` | No record is committed | Named completed-status check rejects it |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/user/domain/User.java` and `wallet/domain/Wallet.java` — reuse protected JPA constructor, factory, generated identity, exact money, injected Clock, microsecond timestamp, explicit mapping, and getter-only conventions.
- `src/main/java/com/paynest/transaction/package-info.java` — existing module seam; add `domain` and `persistence` beneath it without introducing application/API code.
- `src/main/java/com/paynest/{user,wallet}/persistence/*Repository.java` — follow the existing small feature-owned `JpaRepository` convention; no wrapper/interface layer.
- `src/main/resources/db/migration/V1__create_users.sql` and `V2__create_wallets.sql` — immutable predecessors; V3 references Wallet IDs and must not edit these files.
- `src/test/java/com/paynest/MigrationContractTests.java` — extend staged migration/catalog verification through V3 while retaining earlier checksum evidence.
- `src/test/java/com/paynest/PayNestApplicationTests.java` — update final schema/entity round-trip expectations so Hibernate validates all three mappings.
- `src/test/java/com/paynest/support/TestDatabaseSupport.java` — delete Transaction rows before Wallet/User rows after the safety guard.
- `src/test/java/com/paynest/{user, wallet}/*HttpIntegrationTests.java` — update exact expected table inventories only; preserve endpoint behavior.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/resources/db/migration/V3__create_transactions.sql` — create only `paynest.transactions`, two named Wallet FKs, positive/non-NaN amount, distinct-Wallet and completed-only checks, and Sender/Receiver history indexes ordered by `created_at DESC, id DESC`.
- [x] `src/main/java/com/paynest/transaction/domain/Transaction.java` and `TransactionStatus.java` — implement the completed-only factory, exact amount/time validation, non-updatable business columns, required lazy `@ManyToOne` ownership, and no setters/cascades/collections.
- [x] `src/main/java/com/paynest/transaction/persistence/TransactionRepository.java` — expose persistence using the existing repository convention without a separate recording service or update/delete use case.
- [x] `src/test/java/com/paynest/transaction/domain/TransactionTests.java` — verify creation, amount/participant boundaries, immutability shape, association ownership, lazy loading, and absence of cascade/orphan behavior.
- [x] `src/test/java/com/paynest/transaction/TransactionPersistenceIntegrationTests.java` — against guarded `paynest_test`, prove persist/load fidelity and raw PostgreSQL rejection of every invalid constraint case.
- [x] `src/test/java/com/paynest/MigrationContractTests.java` — migrate V1 → V2 → V3, validate prior checksums, and assert Transaction columns, named constraints, and exact index definitions from PostgreSQL catalogs.
- [x] `src/test/java/com/paynest/PayNestApplicationTests.java`, `support/TestDatabaseSupport.java`, `user/CurrentUserProfileHttpIntegrationTests.java`, and `wallet/CurrentWalletHttpIntegrationTests.java` — integrate the third table into application startup, cleanup order, Hibernate validation, and exact schema assertions without changing existing API behavior.

**Acceptance Criteria:**
- Given a database migrated through V2, when V3 is applied and Flyway validates, then V1/V2 remain valid and only the Transaction table, required constraints, and two history indexes are added.
- Given two persisted Wallets, when a completed Transaction is created, saved, and reloaded, then its generated ID, exact amount, participants, status, and UTC timestamp are preserved.
- Given the JPA model, when mappings are inspected, then both Wallet associations are required lazy unidirectional owners with no cascade/orphan removal, and Wallet exposes no Transaction collection.
- Given invalid raw Transaction data, when PostgreSQL persistence is attempted, then the applicable named constraint rejects it and no invalid record commits.
- Given the completed implementation, when entity and schema inventories are inspected, then exactly three persisted entities exist and no Transfer entity, table, repository, service, or endpoint has been introduced.

## Implementation Notes

- Added the append-only V3 schema, immutable completed-only Transaction model, and feature-owned repository without introducing any Transfer application or HTTP surface.
- PostgreSQL constraint-authority tests use raw JDBC where the Java factory intentionally cannot represent invalid status or participant states. Named `NOT NULL` constraints are verified through PostgreSQL catalogs because violation messages identify the affected column.
- Review hardening added a frozen V2 checksum checkpoint, cross-transaction durability evidence, restrictive FK deletion checks, exact constraint inventory, and name-independent Wallet relationship inspection.
- Independently verified on Java 25 and disposable native PostgreSQL 18.6: focused Transaction tests passed (7/7), the final complete suite passed (95/95, no failures/errors/skips), and packaging succeeded with tests skipped.

## Spec Change Log

## Review Triage Log

| Reviewer finding | Verdict | Evidence and route |
|---|---|---|
| Blind 1 — raw over-scale NUMERIC input may round | false | The supported Java creation path rejects scale greater than two before persistence; raw SQL is not a product creation path, and `NUMERIC(19,2)` is the approved storage contract. |
| Blind 2 — database permits Transaction UPDATE/DELETE | false | “Append-only V3” describes migration evolution; approved immutability is enforced by no public mutation/use case, non-updatable mappings, and `@Immutable`, not database triggers. |
| Blind 3 — `JpaRepository` exposes delete/update-capable methods | false | The approved Design Notes explicitly retain the project repository convention and prohibit application update/delete use cases rather than inherited methods. |
| Blind 4 — happy persistence test rolls back | low | The test flushes and reloads inside one transaction, so cross-transaction durability is not directly demonstrated; patch the test to commit before reload. |
| Blind 5 — Wallet collection test misses generic associations | low | A differently named `List<Transaction>` relationship could evade the current raw-type/name assertions; patch reflection coverage. |
| Blind 6 — V2 checksum is compared with itself | medium | V2 is applied from the current production file before its checksum is captured, so an accidental historical edit would pass; patch with an immutable V2 fixture. |
| Blind 7 — tests do not require `@Immutable` or attempt updates | false | Non-updatable business columns and absence of public mutation methods already verify the approved observable contract; a specific Hibernate annotation is not itself the requirement. |
| Blind 8 — FK assertions allow `ON DELETE CASCADE` | low | Substring assertions would pass with a cascade action; patch catalog and deletion behavior coverage. |
| Blind 9 — `GENERATED BY DEFAULT` permits explicit IDs | false | `BY DEFAULT` is the approved schema convention already used by User and Wallet; no public or application path accepts a Transaction ID. |
| Blind 10 — constraint inventory uses `contains` | low | An unexpected narrowing constraint could evade the catalog assertion; patch it to assert the exact sorted inventory including the primary key. |
| Verification 1 — V2 checksum verification is self-referential | medium | Pre-verified search confirms only V1 has an immutable fixture; patch with a frozen through-V2 migration checkpoint. |
| Verification 2 — no-cascade database contract lacks evidence | low | Pre-verified catalog and persistence tests do not assert FK delete action or parent-delete rejection; patch both. |
| Verification 3 — Wallet back-collection check misses generics | low | Pre-verified reflection checks raw field types and three names only; patch generic/relationship inspection. |
| Edge 1 — Clock may produce an out-of-range PostgreSQL timestamp | false | Production uses `Clock.systemUTC()` and no caller controls timestamps; PostgreSQL safely rejects an impossible injected test clock without corrupting state. |
| Edge 2 — explicit raw ID may advance beyond the identity sequence | false | Raw SQL ID insertion is outside the application surface and `GENERATED BY DEFAULT` is the adopted project-wide identity convention. |
| Edge 3 — raw over-scale amount can be rounded | false | The only supported factory rejects over-scale values without rounding; direct SQL bypass is outside the product use case. |
| Edge 4 — inherited repository deletes contradict append-only claim | false | The approved spec explicitly permits inherited Spring Data methods while forbidding application update/delete use cases; none were introduced. |

## Design Notes

Use direct JDBC for unsupported-status, missing-participant, nonexistent-Wallet, and other database-authority tests that cannot be represented through the completed-only Java factory. `TransactionRepository` may retain inherited Spring Data methods to match current project conventions; immutability means no application mutation/delete use case, not an extra repository abstraction.

## Verification

**Commands:**
- `mvn.cmd -Dtest=TransactionTests test` — expected: focused domain/mapping tests pass without a database.
- `mvn.cmd test` with `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, `PAYNEST_TEST_DB_PASSWORD`, and `PAYNEST_JWT_SECRET` configured — expected: all tests pass against the dedicated local `paynest_test` PostgreSQL database.
- `mvn.cmd verify -DskipTests` — expected: application compiles and packages successfully.
