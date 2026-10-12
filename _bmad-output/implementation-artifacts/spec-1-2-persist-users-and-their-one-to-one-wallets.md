---
title: 'Story 1.2: Persist Users and Their One-to-One Wallets'
type: 'feature'
created: '2026-10-07'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'NO_VCS'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest has a verified persistence foundation but no account or balance records. Registration and later money movement require durable User and Wallet models with database-backed identity, uniqueness, ownership, and monetary invariants.

**Approach:** Add the User schema/mapping first and verify it as a V1 checkpoint, then append the Wallet schema/mapping as V2. Model Wallet as the required lazy owning side of a unidirectional one-to-one relationship and keep exact balance behavior inside Wallet.

## Boundaries & Constraints

**Always:** Flyway owns append-only DDL and Hibernate remains `validate`; use generated `Long`/`BIGINT` IDs, `Instant`/`TIMESTAMPTZ`, and `BigDecimal`/`NUMERIC(19,2)`; normalize email by trim plus `Locale.ROOT` lowercase; trim names; persist only password hashes; create Wallets as `0.00 USD`; name and verify all specified constraints; keep Wallet→User lazy, required, unidirectional, and free of cascade/orphan removal; prove PostgreSQL behavior with Testcontainers.

**Never:** Add endpoints, DTOs, registration/password encoding, Transaction persistence, public funding, an inverse `User.wallet`, transaction collections, general public setters, plaintext passwords, floating-point money, silent rounding, H2, or speculative service/abstraction layers.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Create User | Valid name, email, hash, fixed clock | Name is trimmed; email is normalized; hash and UTC creation instant round-trip | Reject null, blank, or out-of-bound persisted values before acceptance |
| Duplicate email | Emails differ only by case/outer whitespace | Both normalize identically; `uk_users_email` permits one row | PostgreSQL rejects the duplicate |
| Create Wallet | Persisted User and fixed clock | Generated ID, `0.00`, `USD`, and required User ownership round-trip | `uk_wallets_user` rejects a second Wallet |
| Change balance | Positive amount with at most two decimals | Credit/debit uses exact scale-two arithmetic | Reject non-positive, over-precise, excessive debit, or any negative result without mutation |
| Invalid stored Wallet | Negative balance or non-USD value through JDBC | No invalid row is committed | Named PostgreSQL checks reject it |

</frozen-after-approval>

## Code Map

- `src/main/resources/db/migration/V1__create_users.sql` — create only `paynest.users` and named `uk_users_email`; once the V1 checkpoint passes, do not edit it.
- `src/main/resources/db/migration/V2__create_wallets.sql` — append `paynest.wallets` with `fk_wallets_user`, `uk_wallets_user`, `ck_wallets_usd_currency`, and `ck_wallets_non_negative_balance`; remove the obsolete `.gitkeep`.
- `src/main/java/com/paynest/user/domain/{User,EmailNormalizer}.java` — encapsulate trimmed identity data, normalized email, hash storage, and deterministic creation time without a Wallet field.
- `src/main/java/com/paynest/user/persistence/UserRepository.java` — direct Spring Data repository with ID and normalized-email lookup; do not wrap it in another repository abstraction.
- `src/main/java/com/paynest/wallet/domain/Wallet.java` — own the lazy User association, fixed USD state, and exact credit/debit rules; use a String constant rather than another entity or persisted type.
- `src/main/java/com/paynest/wallet/persistence/WalletRepository.java` — direct repository with the conceptual inverse query by User ID.
- `src/main/java/com/paynest/common/config/ClockConfig.java` — provide the UTC `Clock` later application services will pass into entity creation; entities themselves remain Spring-free.
- `src/test/java/com/paynest/{user/domain/EmailNormalizerTests,wallet/domain/WalletTests}.java` — fast normalization, factory, mapping-shape, and money invariant tests.
- `src/test/java/com/paynest/PayNestApplicationTests.java` — extend the existing Java 25/PostgreSQL context test for final V1+V2 schema, repositories, round trips, and authoritative constraints.
- `src/test/java/com/paynest/MigrationContractTests.java` — migrate an isolated schema to V1, record its checksum/catalog contract, upgrade the same schema to V2, validate, and verify the checksum is unchanged.

## Tasks & Acceptance

**Execution:**
- [x] `V1__create_users.sql`, User domain/repository, normalization tests, and the V1 migration checkpoint — establish and verify User persistence before introducing Wallet or V2.
- [x] `V2__create_wallets.sql`, Wallet domain/repository, and Wallet unit tests — add the owning one-to-one and exact monetary behavior without modifying V1.
- [x] `ClockConfig.java`, `PayNestApplicationTests.java`, and `MigrationContractTests.java` — verify deterministic time, final Hibernate validation, staged migration integrity, catalog definitions, persistence round trips, and database constraint failures on PostgreSQL 18.6.

**Acceptance Criteria:**
- Given a clean database, when migrations stop at V1, then only User domain storage exists, User persistence works, `uk_users_email` has the designed definition, and the application’s User-only checkpoint has passed before Wallet code is added.
- Given the V1 database, when the unchanged migration chain advances to V2, then Flyway validates the prior checksum and Hibernate validates both mapped entities against `users` and `wallets`.
- Given valid entities, when they are persisted and reloaded, then generated IDs, normalized User fields, `0.00 USD`, UTC timestamps, and the required lazy unidirectional ownership are preserved without an inverse field, cascade, or orphan removal.
- Given duplicate email/Wallet ownership or invalid stored Wallet values, when PostgreSQL evaluates the write, then the corresponding named unique, foreign-key, or check constraint rejects it.
- Given Wallet credit/debit inputs, when domain tests exercise valid and invalid amounts, then exact scale-two results are produced and every rejected operation leaves the prior balance unchanged.

## Implementation Notes

- Added the two append-only migrations, field-access User and Wallet mappings, direct Spring Data repositories, locale-independent email normalization, UTC Clock configuration, and exact scale-two Wallet arithmetic without changing the dependency set.
- Verified the historical V1 state in an isolated disposable checkout containing only User/V1: Spring Boot started on Java 25, Hibernate validated the User mapping, the repository persisted and loaded a normalized User, and PostgreSQL exposed only `users` plus Flyway history.
- Verified the final V1→V2 tree on Java 25 against PostgreSQL 18.6: all 13 committed tests passed, including frozen-V1 compatibility, explicit V2 targeting, final Hibernate validation, relationship persistence, named constraint failures, Unicode/boundary behavior, and every edge-matrix scenario.
- Generated the effective POM successfully with the Maven Wrapper; `pom.xml` remains unchanged. The disposable V1 checkpoint tree was removed after verification.
- Applied confirmed review hardening: Unicode-aware/code-point normalization, microsecond timestamp precision, NaN rejection, durable V1 checksum evidence, Wallet dirty-checking persistence, schema default/nullability assertions, equality boundaries, and safe validation ordering for extreme-scale amounts.

## Spec Change Log

## Review Triage Log

| ID | Verdict | Evidence and route |
|---|---|---|
| B1 | medium | PostgreSQL `NUMERIC 'NaN'` satisfies an ordinary non-negative comparison, so the current Wallet check admits a non-monetary value. Patch the named check and cover the JDBC failure. |
| B2 | low | Java `Instant` can carry finer precision than PostgreSQL `TIMESTAMPTZ`, so a normal create/reload can change the value below microseconds. Patch both factories to a documented microsecond boundary and test it. |
| B3 | medium | `String.trim()` leaves Unicode whitespace such as U+2003, allowing a visually blank two-character name. Patch name normalization with Unicode-aware stripping and tests. |
| B4 | medium | `String.trim()` leaves Unicode whitespace around email, allowing equivalent visible addresses to normalize differently. Patch email normalization with Unicode-aware stripping and tests. |
| B5 | medium | `String.length()` counts UTF-16 units while PostgreSQL `VARCHAR(n)` counts characters; supplementary characters therefore produce observable boundary mismatches. Patch name/email limits to count code points and test them. |
| B6 | medium | Capturing the checksum from the same current V1 file cannot detect a later rewrite on a clean database. Patch the migration test to provision V1 from a frozen test fixture before validating the production chain. |
| B7 | medium | The upgrade test currently targets latest, so a future V3 would make the V1→V2 contract consume extra migrations and fail for the wrong reason. Patch the final target to version 2. |
| B8 | false | The approved design deliberately uses a staged disposable User-only application checkpoint rather than committed duplicate application configuration; that Java 25 Spring/JPA/repository checkpoint passed, and final Hibernate validation still covers User. |
| B9 | medium | No integration test proves a mutated Wallet balance is written and round-trips as `NUMERIC(19,2)`; an accidental non-updatable mapping could pass all current tests. Patch the existing repository round-trip test. |
| B10 | low | The SQL visibly declares defaults and non-null columns, but the migration contract does not execute or inspect them. Patch focused catalog/default assertions without adding product behavior. |
| V1 | medium | Independently confirms B6: the current checksum comparison is self-derived and cannot catch a rewritten shared V1. Patch with the same frozen-fixture upgrade test. |
| V2 | medium | Valid name/email/hash equality boundaries are untested, so off-by-one regressions could reject values that fit the schema. Patch accepted-boundary unit cases. |
| V3 | medium | Exact maximum credit and full-balance debit are valid but untested; `>=` regressions would remain green. Patch equality-boundary Wallet tests. |
| E1 | medium | Independently confirms B4: Unicode edge whitespace survives `trim()` and changes canonical email identity. Patch with Unicode-aware stripping. |
| E2 | medium | Independently confirms B3: Unicode-only whitespace can satisfy the name minimum. Patch with Unicode-aware stripping and blank rejection. |
| E3 | false | `UserRepository.findByEmail` intentionally accepts a parameter named `normalizedEmail`; normalization belongs to the calling use case and the repository is not an input-validation layer. Current callers comply. |
| E4 | false | The binding architecture requires `uk_users_email UNIQUE(email)` over normalized stored values, and all production construction normalizes first. A functional lower/trim index would change that explicit schema contract rather than fix this implementation. |
| E5 | false | Story 1.2 has no concurrent balance use case, and the approved final strategy is ordered pessimistic row locking in the later Transfer story. Adding `@Version` now would conflict with that selected design. |
| E6 | medium | An extreme negative-scale `BigDecimal` reaches scale conversion before the existing maximum comparison and can escape as `ArithmeticException`. Patch validation order so sign/range are rejected before scale normalization. |
| E7 | false | Application/domain paths already reject over-precise money before persistence; PostgreSQL typmod coercion for the explicitly required `NUMERIC(19,2)` occurs before checks. Replacing it with unconstrained `NUMERIC` would violate the schema contract for an unsupported direct writer. |

## Design Notes

The V1-only application state is a staged implementation gate plus a durable target-V1 migration-contract test. A permanent second Spring Boot configuration would add disproportionate scaffolding; the final application context remains strict and validates both entities against V2. Standard argument/state exceptions are sufficient for Wallet domain failures in this story; the REST exception contract arrives with API stories.

Schema contract: `users(id BIGINT identity, name VARCHAR(100), email VARCHAR(254), password_hash VARCHAR(255), created_at TIMESTAMPTZ)` with all values non-null; `wallets(id BIGINT identity, user_id BIGINT, balance NUMERIC(19,2) DEFAULT 0.00, currency CHAR(3) DEFAULT 'USD', created_at TIMESTAMPTZ)` with all values non-null. The Wallet FK references `users(id)` with restrictive deletion, its `user_id` is unique, and its checks require `balance >= 0.00` and `currency = 'USD'`.

## Verification

**Commands:**
- `./mvnw test` — expected: unit tests and PostgreSQL 18.6 Testcontainers integration tests pass on Java 25; Flyway validates V1→V2 and Hibernate validates the final schema.
- `./mvnw help:effective-pom` — expected: the existing approved dependency set remains unchanged; Story 1.2 adds no library.
