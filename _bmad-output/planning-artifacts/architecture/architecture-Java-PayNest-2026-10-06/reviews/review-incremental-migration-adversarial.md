# Adversarial Review: Incremental-Migration Architecture-to-Epics Seam

**Reviewed:** `ARCHITECTURE-SPINE.md`, `solution-design.md`, and `_bmad-output/planning-artifacts/epics.md`  
**Lens:** adversarial  
**Date:** 2026-10-06

## Verdict

The final database shape is coherent: `V1` can create `users`, `V2` can create `wallets` with its User foreign key, and `V3` can create `transactions` with both Wallet foreign keys and the two history indexes. No cyclic foreign key or impossible final Hibernate mapping was found.

The seam is nevertheless **not implementation-independent yet**. A builder following the learner roadmap produces a different sequence from a builder following the story order, and Story 1.1 leaves enough ambiguity for a no-op baseline migration to consume or displace the reserved `V1`. Story 1.2 also combines two architecture phases, so the documents do not require the intermediate `V1 + User` application state to start successfully. The other material gap is that a public Transfer is accepted before its required concurrency safety is implemented.

The safest reconciliation is to declare one canonical state machine and test it:

```text
S0: empty database; Flyway has zero versioned migrations; no entities; Hibernate validate is vacuous
S1: V1 users -> User mapping -> clean startup/validate
S2: V2 wallets -> Wallet mapping -> clean startup/validate
S3: V3 transactions + history indexes -> Transaction mapping -> clean startup/validate
S4: Transfer endpoint is accepted only after ordered locking is present, or is explicitly marked incomplete until the locking story
```

If Story 1.2 intentionally owns both S1 and S2, its acceptance criteria should still require both checkpoints independently. The architecture roadmap and AR29 must also be changed to match the chosen Story 2 ordering, or the stories must be reordered to match them.

## Post-fix recheck — 2026-10-06

**Verdict: PASS for the targeted architecture-to-epics seam.** The reconciliation edits remove the incompatible implementation paths identified above. The original findings remain below as the pre-fix audit trail; they no longer describe the current normative sequence where marked resolved here.

| Rechecked concern | Status | Current evidence |
|---|---|---|
| Zero-version foundation | Resolved | Story 1.1 now says no versioned migration is consumed and the persistence smoke test starts without any versioned domain migration. `V1` is therefore unambiguously reserved for User. |
| Exact V1/V2/V3 sequence and checkpoints | Resolved | Story 1.2 requires a runnable V1-only User checkpoint and an upgrade of the same database to V2 with both mappings validated. Story 2.1 upgrades a V2 database to V3 and validates all three mappings. AR29 and solution-design §17 now use the same Transaction-first, internal-Transfer-next sequence. |
| Shared schema and startup contract | Resolved | Story 1.1 requires Flyway and Hibernate to target the same configured PostgreSQL schema; solution-design §11.1 identifies `public` as the default. The entity checkpoints require Flyway/Hibernate validation, and Story 4.3 proves clean startup applies migrations before validating the resulting schema. |
| Append-only, checksum, and catalog evidence | Resolved | AD-7/AR10 make migrations append-only. The V1→V2 checkpoint preserves the V1 checksum and asserts Wallet constraint definitions; the V2→V3 checkpoint preserves V1/V2 checksums and asserts Transaction constraints and indexes through the PostgreSQL catalog. |
| V3 history-index intent | Resolved | Story 2.1 explicitly states that both named history indexes are intentionally created in V3 with the Transaction table so Story 3.1 does not rewrite V3. |
| Public Transfer exposure | Resolved | Story 2.2 is service-level only and explicitly forbids the public mapping. Story 2.4 introduces `POST /api/wallet/transfers` only after ordered `PESSIMISTIC_WRITE` locking is active. The roadmap and AR29 match this gate. |

No incompatible migration ordering, foreign-key timing, index timing, Hibernate-validation startup path, or forward dependency remains in the rechecked scope. One optional evidence refinement remains: the V1-only checkpoint proves email uniqueness behavior but does not name a PostgreSQL catalog assertion for `uk_users_email` as explicitly as the V2 and V3 checkpoints do. Adding that assertion would make the three migration gates perfectly symmetric, but it is not a blocking inconsistency because the exact constraint is normative in solution-design §10.2 and is behaviorally tested in Story 1.2.

## Independent implementations

### Implementation A — architecture-roadmap literal

1. Story/Foundation starts Flyway with no versioned migration and no entity mappings.
2. Phase 3 adds `V1__create_users.sql`, then the User mapping and repository, and proves startup at Flyway version 1.
3. Phase 4 adds `V2__create_wallets.sql`, then the Wallet mapping and repository, and proves startup at version 2.
4. Phase 9 builds non-public basic Transfer internals before a Transaction table exists.
5. Phase 10 adds `V3__create_transactions.sql`, the Transaction mapping, history indexes, persistence, and the public Transfer endpoint.
6. Ordered pessimistic locking is added later, so the endpoint is temporarily not concurrency-complete.

### Implementation B — epics literal

1. Story 1.1 may add an empty/baseline migration because it says “Flyway migration” succeeds, despite also saying no domain tables are front-loaded.
2. Story 1.2 introduces `V1`, `V2`, User, and Wallet in one delivery unit; it never has to demonstrate a runnable version-1-only application.
3. Story 2.1 introduces `V3`, Transaction, repository persistence, and history indexes before any Transfer implementation.
4. Story 2.2 exposes the public Transfer endpoint without the locking required by Story 2.4.

Both implementations can reach the same final schema, but they cannot both obey the current version allocation and the binding sequence language. Implementation B can collide with `V1` at the foundation; A and B directly disagree over whether basic Transfer internals or `V3` comes first.

## Findings

### 1. The foundation has no canonical zero-migration behavior

- **Location:** `epics.md` — Story 1.1, empty PostgreSQL and persistence-smoke acceptance criteria; `solution-design.md` §11.1 and §17 phases 1–3
- **Trigger condition:** A developer reads “Flyway migration … succeed” as requiring a versioned baseline migration even though `V1__create_users.sql` is reserved for the first domain table and the foundation must front-load no domain table.
- **Guard snippet:** State explicitly: “Story 1.1 contains no versioned migration. `Flyway.migrate()` may create/manage its schema-history table and must report zero pending/applied versioned migrations. `V1` remains reserved for Story 1.2’s User checkpoint.”
- **Potential consequence:** One implementation creates `V1__baseline.sql`, another creates no migration, and a third renumbers the domain chain to V2–V4. Their schema histories and later story artifacts are incompatible.

### 2. The binding roadmap and epic order disagree about `V3`

- **Location:** `solution-design.md` §17 phases 9–10; `epics.md` AR29; `epics.md` Stories 2.1–2.2
- **Trigger condition:** The architecture/AR29 implementer builds basic Transfer internals before Transaction/V3, while the epic implementer must deliver Transaction/V3 in Story 2.1 before Transfer in Story 2.2.
- **Guard snippet:** Choose one order and make all three locations identical. If the epics are authoritative, revise AR29 and roadmap phases to “Transaction/V3 persistence, then Transfer use case/endpoint.” If the learning roadmap is authoritative, create a Basic Transfer Internals story before the Transaction story and keep the endpoint private until Transaction recording exists.
- **Potential consequence:** Teams cannot agree which code and tests belong in Story 2.1; story snapshots, review gates, and implementation handoffs differ even though the final schema eventually converges.

### 3. Story 1.2 collapses two architecture gates without preserving the intermediate gate

- **Location:** `solution-design.md` §17 phases 3–4; `epics.md` Story 1.2
- **Trigger condition:** A developer adds V1, V2, User, and Wallet together and runs tests only after both migrations have applied.
- **Guard snippet:** Add two acceptance checkpoints inside Story 1.2: (a) migrate a clean database to V1 and start with only User mapped; (b) migrate that same database to V2 and start with User and Wallet mapped. Alternatively split Story 1.2 into separate User and Wallet stories.
- **Potential consequence:** The claimed just-in-time sequence is documentary only. A broken V1-only state or an accidental Wallet dependency in User code can ship unnoticed, and the architecture’s phase-3 gate is not reproducible from the epics.

### 4. Migration-before-mapping startup order is implied, not accepted

- **Location:** `ARCHITECTURE-SPINE.md` AD-7; `solution-design.md` §11.1; `epics.md` Stories 1.2 and 2.1
- **Trigger condition:** An implementer commits or enables an entity mapping before its migration is available to the same runtime profile while Hibernate uses `ddl-auto=validate`.
- **Guard snippet:** For each entity checkpoint require: “On a clean database, Flyway applies the story’s next migration before JPA creates the EntityManagerFactory; Hibernate validation then succeeds without schema generation.” Also assert the initialization order in a Spring Boot integration test rather than relying only on default auto-configuration.
- **Potential consequence:** Application startup fails with a missing-table or missing-column validation error; another implementation may hide the failure by disabling validation in the affected profile.

### 5. Flyway and Hibernate have no explicit shared-schema contract

- **Location:** `solution-design.md` §§10–11; `epics.md` Stories 1.1 and 4.3; `ARCHITECTURE-SPINE.md` AD-7
- **Trigger condition:** A profile sets a PostgreSQL `currentSchema`, Flyway schema, or Hibernate default schema differently from another profile or component.
- **Guard snippet:** Name the MVP schema (for example `public`) and require the datasource, Flyway, and Hibernate default-schema settings to target it consistently in local, test, and Compose profiles. Add a startup assertion using a non-default search path or explicitly prohibit custom schemas for MVP.
- **Potential consequence:** Flyway successfully creates V1–V3 in one schema while Hibernate validates another, producing profile-specific startup failures despite both subsystems appearing correctly configured.

### 6. Story handoffs omit the exact constraint and index identities

- **Location:** `solution-design.md` §§10.2–10.3; `epics.md` AR11, Story 1.2, and Story 2.1
- **Trigger condition:** A story implementer sees only “named constraints” and “Sender and Receiver history indexes” and chooses names different from `uk_users_email`, `uk_wallets_user`, `fk_wallets_user`, the five named Transaction constraints, and `idx_transactions_sender_history` / `idx_transactions_receiver_history`.
- **Guard snippet:** Put the exact names in the affected story acceptance criteria or add an explicit normative reference to solution-design §§10.2–10.3. Include catalog assertions for those identities.
- **Potential consequence:** Constraint-aware exception translation and schema tests vary between implementations; a generic SQLSTATE `23505` handler may incorrectly translate a Wallet uniqueness defect as `EMAIL_ALREADY_EXISTS`.

### 7. Migration ownership does not explicitly follow feature ownership

- **Location:** `ARCHITECTURE-SPINE.md` AD-3 and AD-7; `solution-design.md` §§3.2 and 11.1; `epics.md` AR5 and AR10
- **Trigger condition:** Auth/registration, Transfer, or Transaction-history work changes another feature’s table migration because repository ownership is clear but DDL ownership in the shared migration directory is not.
- **Guard snippet:** Declare: “User owns V1/users DDL, Wallet owns V2/wallets DDL, and Transaction owns V3/transactions DDL and its query indexes. Cross-feature coordinators consume those schemas and never redefine them.”
- **Potential consequence:** The code remains package-owned while schema changes become coordinator-owned, creating conflicting edits and weakening the very module seam the architecture is meant to protect.

### 8. Append-only evolution is architecture text but not a story-level acceptance guard

- **Location:** `solution-design.md` §11.1; `epics.md` AR10; Stories 2.1 and 3.1
- **Trigger condition:** History implementation or later tuning discovers a missing/insufficient index and edits the already-shared `V3` rather than appending `V4`.
- **Guard snippet:** Add to each schema-affecting story: “Previously applied migration files and checksums remain unchanged; any correction or later index change uses the next versioned migration.”
- **Potential consequence:** Existing developer/test databases fail Flyway checksum validation while clean CI databases pass, so delivery becomes non-reproducible.

### 9. “Just in time” is table-based for V3 but query-based readers can reasonably infer a different index time

- **Location:** `ARCHITECTURE-SPINE.md` AD-7; `solution-design.md` §§10.3 and 11.1; `epics.md` AR10, Story 2.1, and Story 3.1
- **Trigger condition:** One implementer treats history indexes as part of the Transaction table’s initial contract and creates them in V3; another interprets just-in-time literally by first consumer and defers them until the Story 3.1 history query.
- **Guard snippet:** State that V3’s history indexes are an intentional exception to first-query timing because they are part of the Transaction persistence contract, or move them to a new migration owned by Story 3.1 and update AD-7/AR10 everywhere.
- **Potential consequence:** Implementations allocate different migration contents/versions, and a later attempt to reconcile them requires checksum-breaking edits or an extra compensating migration.

### 10. The public Transfer story depends forward on the concurrency story for required correctness

- **Location:** `ARCHITECTURE-SPINE.md` AD-5–AD-6; `solution-design.md` §§5–6 and §17 phases 10, 13–15; `epics.md` Stories 2.2 and 2.4
- **Trigger condition:** Story 2.2 is accepted and its public endpoint is considered complete before Story 2.4 adds ordered pessimistic locks.
- **Guard snippet:** Either include ordered locking in Story 2.2’s done criteria and reserve Story 2.4 for adversarial proof/hardening, or explicitly mark Story 2.2 as an internal/non-releasable learning checkpoint whose endpoint cannot satisfy Epic 2 until Story 2.4 passes.
- **Potential consequence:** A demonstrably overspendable product endpoint is treated as delivered, violating FR9/NFR7 and the spine’s required Transfer flow until a future story repairs it.

### 11. There is no staged migration/upgrade-chain acceptance test

- **Location:** `epics.md` Stories 1.1, 1.2, 2.1, and 4.3; `solution-design.md` §11.1 and §12
- **Trigger condition:** Tests always create a clean database and apply all migrations currently present in one run.
- **Guard snippet:** Add one Testcontainers migration-chain test that stops/asserts at V1, upgrades/asserts at V2, upgrades/asserts at V3, restarts at V3, and verifies that previous checksums are unchanged. If Story 1.2 remains combined, this test is the minimum evidence that its internal checkpoints are real.
- **Potential consequence:** Clean final startup can pass while incremental developer databases fail due to a modified migration, wrong version dependency, or profile-specific validation behavior.

### 12. Hibernate validation is presented as broader schema evidence than it provides

- **Location:** `epics.md` Story 4.3; `solution-design.md` §§10–12; `ARCHITECTURE-SPINE.md` AD-7 and AD-11
- **Trigger condition:** The clean Compose smoke test passes because Hibernate finds compatible tables and columns, and the result is treated as proof that named checks, foreign keys, uniqueness, and history indexes are present.
- **Guard snippet:** Separate the claims: Hibernate validates mapped tables/columns; direct PostgreSQL negative tests prove checks/FKs/uniqueness; `pg_catalog` or `information_schema` assertions prove exact constraint and index definitions. Keep all three as required evidence.
- **Potential consequence:** A migration can omit or misdefine a critical check/index while application startup still succeeds, producing false confidence in the incremental schema.

### 13. ORM-level invalid-entity tests can fail before exercising the migration constraints

- **Location:** `epics.md` Story 1.2 relationship tests and Story 2.1 invalid-Transaction tests; `solution-design.md` §12.2
- **Trigger condition:** Bean/entity validation, transient-reference checks, or domain constructors reject invalid data before PostgreSQL receives the statement.
- **Guard snippet:** For each named database constraint, include a direct JDBC or `@Sql` negative case and assert PostgreSQL rejection/constraint identity; keep domain/JPA rejection as a separate test.
- **Potential consequence:** Tests appear to prove V1–V3 constraints even if the SQL migration omitted them, so independently produced migrations can differ materially while sharing a green suite.

### 14. The over-precision rule has no single declared enforcement layer at the migration seam

- **Location:** `ARCHITECTURE-SPINE.md` AD-7; `solution-design.md` §§4, 8.2, 10.2, and 10.4; `epics.md` NFR4, Story 1.2, and Story 2.2
- **Trigger condition:** A direct JDBC/test-fixture insert supplies more than two fractional digits to `NUMERIC(19,2)`, while application DTO/domain validation is bypassed.
- **Guard snippet:** Declare explicitly that excess scale is rejected by boundary/domain code before SQL and is not proven by the `NUMERIC(19,2)` typmod alone; test the application path. If database-level rejection is required, redesign the column/check strategy because PostgreSQL can coerce scale before a check observes the original input.
- **Potential consequence:** One implementation expects PostgreSQL to reject and another expects application-only rejection; direct writes can be rounded even though the cross-document rule says excess precision is rejected rather than rounded.

## Required reconciliation before final validation

1. Define Story 1.1 as a zero-versioned-migration state and reserve V1 unambiguously.
2. Align AR29 and solution-design §17 with Stories 2.1–2.2, or reorder/split those stories.
3. Preserve and test V1-only and V2-only bootable checkpoints even if Story 1.2 remains combined.
4. Declare a shared PostgreSQL schema and Flyway-before-Hibernate startup contract for every profile.
5. Carry exact constraint/index names and append-only behavior into schema-affecting story criteria.
6. Resolve whether V3 intentionally front-loads history indexes.
7. Do not treat Story 2.2 as releasably correct before ordered locking exists.
8. Add staged Flyway upgrade, catalog, and direct database-constraint evidence rather than relying on final clean startup or Hibernate validation alone.

## Canonical findings JSON

```json
[
  {
    "lens": "adversarial",
    "location": "epics.md Story 1.1; solution-design.md §11.1 and §17 phases 1–3",
    "trigger_condition": "The foundation says Flyway migration succeeds while reserving V1 for users and forbidding front-loaded domain tables.",
    "guard_snippet": "Declare that Story 1.1 has zero versioned migrations and reserves V1 for the User checkpoint.",
    "potential_consequence": "Implementations create incompatible baseline/version histories."
  },
  {
    "lens": "adversarial",
    "location": "solution-design.md §17 phases 9–10; epics.md AR29 and Stories 2.1–2.2",
    "trigger_condition": "The roadmap orders Transfer internals before V3, while the stories order V3 before Transfer.",
    "guard_snippet": "Select one order and make the roadmap, AR29, and story sequence identical.",
    "potential_consequence": "Story scope and delivery checkpoints are incompatible across implementers."
  },
  {
    "lens": "adversarial",
    "location": "solution-design.md §17 phases 3–4; epics.md Story 1.2",
    "trigger_condition": "One story combines V1/User and V2/Wallet without requiring a runnable V1-only checkpoint.",
    "guard_snippet": "Test V1 plus User independently before upgrading the same database to V2 plus Wallet, or split the story.",
    "potential_consequence": "The just-in-time User milestone can be broken without detection."
  },
  {
    "lens": "adversarial",
    "location": "ARCHITECTURE-SPINE.md AD-7; solution-design.md §11.1; epics.md Stories 1.2 and 2.1",
    "trigger_condition": "Entity mappings can be enabled before their migrations while Hibernate validation is active.",
    "guard_snippet": "Require Flyway to apply each migration before EntityManagerFactory creation and prove clean startup at each version.",
    "potential_consequence": "Startup fails or validation is disabled to mask missing tables."
  },
  {
    "lens": "adversarial",
    "location": "solution-design.md §§10–11; epics.md Stories 1.1 and 4.3",
    "trigger_condition": "Flyway and Hibernate can target different PostgreSQL schemas across profiles.",
    "guard_snippet": "Declare one schema and align datasource, Flyway, and Hibernate schema settings.",
    "potential_consequence": "Migrations succeed while Hibernate validates the wrong schema."
  },
  {
    "lens": "adversarial",
    "location": "solution-design.md §§10.2–10.3; epics.md AR11 and Stories 1.2/2.1",
    "trigger_condition": "Stories require named constraints/indexes but omit the exact normative names.",
    "guard_snippet": "Embed the exact names or normatively reference solution-design §§10.2–10.3 and assert the catalog.",
    "potential_consequence": "Exception classification and schema tests differ across implementations."
  },
  {
    "lens": "adversarial",
    "location": "ARCHITECTURE-SPINE.md AD-3/AD-7; solution-design.md §§3.2/11.1; epics.md AR5/AR10",
    "trigger_condition": "Repository ownership is explicit but shared-directory DDL ownership is not.",
    "guard_snippet": "Assign users DDL to User, wallets DDL to Wallet, and transactions/index DDL to Transaction.",
    "potential_consequence": "Cross-feature coordinators redefine schemas owned by other modules."
  },
  {
    "lens": "adversarial",
    "location": "solution-design.md §11.1; epics.md AR10 and Stories 2.1/3.1",
    "trigger_condition": "A later story edits a previously shared migration instead of appending a new version.",
    "guard_snippet": "Add append-only/checksum preservation to every schema-affecting story acceptance criteria.",
    "potential_consequence": "Existing databases fail Flyway validation while clean CI passes."
  },
  {
    "lens": "adversarial",
    "location": "ARCHITECTURE-SPINE.md AD-7; solution-design.md §§10.3/11.1; epics.md AR10 and Stories 2.1/3.1",
    "trigger_condition": "Just-in-time can mean Transaction-table time or first-history-query time for history indexes.",
    "guard_snippet": "Document V3 indexes as an intentional exception or move them to a history-owned next migration everywhere.",
    "potential_consequence": "Implementations produce incompatible V3 contents and checksums."
  },
  {
    "lens": "adversarial",
    "location": "ARCHITECTURE-SPINE.md AD-5–AD-6; solution-design.md §§5–6; epics.md Stories 2.2/2.4",
    "trigger_condition": "The public Transfer endpoint is accepted before required ordered locking exists.",
    "guard_snippet": "Move locking into Story 2.2 done criteria or mark Story 2.2 explicitly non-releasable until Story 2.4.",
    "potential_consequence": "A delivered endpoint can overspend under concurrency and depends on a future story for correctness."
  },
  {
    "lens": "adversarial",
    "location": "epics.md Stories 1.1/1.2/2.1/4.3; solution-design.md §§11–12",
    "trigger_condition": "Tests apply all present migrations only to a clean database and never prove staged upgrades/checksums.",
    "guard_snippet": "Test V1, upgrade to V2, upgrade to V3, restart, and verify unchanged checksums.",
    "potential_consequence": "Incremental databases fail even though final clean startup passes."
  },
  {
    "lens": "adversarial",
    "location": "epics.md Story 4.3; solution-design.md §§10–12; ARCHITECTURE-SPINE.md AD-7/AD-11",
    "trigger_condition": "Hibernate startup validation is treated as proof of checks, FKs, uniqueness, and indexes.",
    "guard_snippet": "Use Hibernate for mapping compatibility, direct negative tests for constraints, and catalog assertions for exact indexes/constraints.",
    "potential_consequence": "Critical migration objects can be absent while startup remains green."
  },
  {
    "lens": "adversarial",
    "location": "epics.md Stories 1.2/2.1; solution-design.md §12.2",
    "trigger_condition": "ORM/domain validation rejects bad data before PostgreSQL constraints are exercised.",
    "guard_snippet": "Add direct JDBC or SQL negative tests for every named constraint and assert the database constraint identity.",
    "potential_consequence": "Green tests do not prove the SQL migrations contain the promised constraints."
  },
  {
    "lens": "adversarial",
    "location": "ARCHITECTURE-SPINE.md AD-7; solution-design.md §§4/8.2/10.2/10.4; epics.md NFR4 and Stories 1.2/2.2",
    "trigger_condition": "Direct writes with excess fractional digits bypass application validation and rely on NUMERIC(19,2).",
    "guard_snippet": "Declare application/domain scale rejection as authoritative or redesign for explicit database rejection; test the chosen layer.",
    "potential_consequence": "One implementation silently rounds where the documents promise rejection."
  }
]
```
