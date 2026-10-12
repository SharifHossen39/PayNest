# Incremental-Migration Architecture Rubric Review

## Gate verdict — PASS WITH ONE MEDIUM REFINEMENT

The just-in-time Flyway change preserves the finalized PRD and is coherent across the architecture spine, companion solution design, memlog, and story plan. All twelve architecture decisions remain mechanically complete and substantively enforceable. No critical or high-severity defect was found.

One medium-strength divergence control should be promoted into AD-7 itself: the spine says that tables arrive User → Wallet → Transaction, but leaves the exact `V1`/`V2`/`V3` ownership and the append-only rule only in downstream/companion material. That is enough to guide this current plan, but not enough for the spine alone to prevent independently implemented migration files from colliding or a shared migration from being rewritten.

## Review basis

Reviewed as one contract set:

- `ARCHITECTURE-SPINE.md`, with emphasis on every AD's `Binds` / `Prevents` / `Rule` structure and on amended AD-7;
- `.memlog.md`, including the superseding just-in-time migration decision;
- `solution-design.md`, especially §§10, 11, 12, 14, 17, and the Flyway ADR;
- finalized `prd.md`, especially FR-1..FR-14, §§10, 17, 19–21, and AC-AUTH / AC-WALLET / AC-TRANSFER / AC-TXN / AC-QUALITY;
- `epics.md`, especially AR10 and Stories 1.1, 1.2, and 2.1;
- the BMad architecture good-spine checklist.

The deterministic spine linter returned `ok: true` with zero findings. AD identifiers are contiguous from AD-1 through AD-12; each AD has `Binds`, `Prevents`, and `Rule`; no template placeholders remain; stack entries are pinned.

## AD-by-AD rubric walk

| AD | Structure | Enforceability | Verdict |
|---|---|---|---|
| AD-1 — One modular monolith | Complete; binds MVP topology and states the prohibited divergence. | Exact counts for application, deployment, database, and transaction manager are reviewable; prohibited infrastructure is explicit. | Pass |
| AD-2 — Three-entity domain and ownership | Complete; ownership and forbidden fourth concepts are named. | Entity/table/repository inspection can prove the rule; FK ownership and absence of Wallet Transaction collections are unambiguous. | Pass |
| AD-3 — Feature seams | Complete; dependency direction is reinforced by the diagram. | Controller limits, repository ownership, approved coordinators, and the Auth/User acyclic direction are testable by package/dependency review. Subjective helper-extraction language governs optional refactoring only and cannot weaken the mandatory seams. | Pass |
| AD-4 — Atomic registration | Complete; binds product behavior and rollback evidence. | One outer transaction, exact registration steps, the authoritative unique constraint, and rollback semantics are directly integration-testable. | Pass |
| AD-5 — Atomic transfer | Complete; names transaction owner, state changes, and forbidden failed records. | One service boundary and all-or-nothing debit/credit/insert behavior are directly testable. | Pass |
| AD-6 — Ordered pessimistic locks | Complete; divergence mechanism, isolation, ordering, timeout, and error mapping are fixed. | Repository query inspection plus separate-transaction PostgreSQL tests can prove every clause. | Pass |
| AD-7 — Schema, money, time, migrations | Mechanically complete and materially improved by the just-in-time rule. | Types, validation posture, checks, mapping-time table introduction, and ordering are enforceable. The exact migration partition and append-only policy need promotion into the AD to make the spine independently collision-proof. | Pass with medium refinement |
| AD-8 — JWT and trusted identity | Complete; trust boundary and forbidden auth scope are explicit. | Filter/principal behavior, public/protected operation count, password profile, and excluded features are all inspectable/testable. | Pass |
| AD-9 — Product operations and errors | Complete; endpoint count and error vocabulary are fixed. | OpenAPI contract tests and error integration tests can prove the exact operation count, envelope, and codes. | Pass |
| AD-10 — Persistence/DTO boundary | Complete; fetch, serialization, cascade, and inverse-mapping divergences are covered. | Mapping inspection and bounded history query tests prove the rule. | Pass |
| AD-11 — PostgreSQL verification | Complete; identifies where doubles or H2 would give false confidence. | The required Testcontainers, concurrency, query-shape, migration, security, and OpenAPI evidence is explicit. | Pass |
| AD-12 — Funding/delivery boundary | Complete; prevents product-scope and infrastructure expansion. | Route/classpath inspection, Compose service count, and secret/config checks can prove the rule. | Pass |

## Focused AD-7 assessment

### What is now consistent

The change is represented consistently at each level:

| Build point | Architecture/plan contract |
|---|---|
| Foundation | Configure datasource, Flyway, Testcontainers, and Hibernate validation; introduce no User, Wallet, or Transaction table. |
| User mapping | Add `V1__create_users.sql`, containing only `users` and its named constraints. |
| Wallet mapping | Add `V2__create_wallets.sql`, containing only `wallets`, its User FK/uniqueness, and Wallet checks. |
| Transaction mapping | Add `V3__create_transactions.sql`, containing only `transactions`, its FKs/checks, and both history indexes. |
| Later schema evolution | Append a new versioned migration; do not rewrite an already-shared migration. |

This appears explicitly in solution-design §11.1, roadmap phases 2–4 and 10, epics AR10, Story 1.1, Story 1.2, and Story 2.1. AD-7 accurately captures the architectural intent: schema ownership remains Flyway-only, Hibernate remains validation-only, and tables are no longer front-loaded.

There is no `ddl-auto=validate` contradiction. In the foundation stage there are no domain entity mappings to validate, so an empty domain schema is valid. When each mapping exists, its matching migration is also present before Hibernate validation. A final clean startup applies the whole `V1` → `V2` → `V3` chain before validating all three mappings.

### MEDIUM — AD-7 does not independently freeze the migration split or append-only rule

**Evidence:** AD-7 currently says only that tables are introduced “User, then Wallet, then Transaction and its history indexes.” It does not name the migration versions/files, state that each migration owns only its corresponding table/constraints, prohibit domain tables in the foundation explicitly, or state that a shared applied migration is immutable. Those controls exist in `solution-design.md` and `epics.md`, but the architecture spine is intended to remain the durable compatibility contract.

**Divergence still possible from the spine alone:**

- two implementers can assign conflicting Flyway versions or combine User and Wallet into one file while still claiming they followed “User, then Wallet”;
- a later implementer can edit `V1`, `V2`, or `V3` instead of appending `V4`, creating Flyway checksum divergence between existing and clean databases;
- a foundation implementer can add an empty/baseline domain migration and argue that no table was introduced, creating an unnecessary migration sequence not represented by the agreed plan.

**Recommended autofix:** amend AD-7's `Prevents` to include front-loaded tables, migration-version collisions, and rewritten shared migrations; amend its `Rule` to name `V1__create_users.sql`, `V2__create_wallets.sql`, and `V3__create_transactions.sql`, assign each only its agreed table/constraints/indexes, require no domain-table migration in the foundation, and require later changes to append a new versioned file. This is a spine-hardening change only; it does not alter product scope or story outcomes.

### Supporting AD-7 controls that already pass

- AD-2 fixes the three persisted domain entities and FK ownership.
- AD-4 makes normalized-email uniqueness authoritative and registration atomic.
- AD-5 and AD-6 preserve exact transfer state changes and concurrency integrity.
- AD-10 keeps the mapped object graph bounded.
- AD-11 requires real PostgreSQL Flyway/mapping/constraint tests.
- AD-12 prevents fixture funding from entering production routes/classpath; solution-design §11.1 separately excludes fixture data from versioned production migrations.

## Finalized PRD non-regression check

The migration split does not weaken or reinterpret the finalized PRD:

| PRD obligation | Effect of the change | Verdict |
|---|---|---|
| Version schema changes through a migration tool; normal runtime must not auto-update (§17). | Flyway remains exclusive schema owner; Hibernate remains `validate`. | Preserved |
| Exact `users`, `wallets`, and `transactions` DDL, named integrity controls, and history indexes (§17). | The same schema is partitioned across `V1`, `V2`, and `V3`; no column, FK, check, uniqueness rule, or index is removed. | Preserved |
| Exactly three persisted domain entities (AC-QUALITY-4). | The domain still contains only User, Wallet, and Transaction. Flyway's metadata table is infrastructure metadata, not a domain entity. | Preserved |
| Atomic User+Wallet registration and exactly one Wallet (FR-2, AC-AUTH-1..3, AC-WALLET-1/4). | Runtime transaction behavior is unchanged; prior schema migrations merely prepare both required tables. | Preserved |
| Atomic transfer and one completed Transaction (FR-8..10, AC-TRANSFER, AC-TXN-5). | Transaction schema arrives before the transfer endpoint story uses it; runtime atomicity and rollback remain unchanged. | Preserved |
| PostgreSQL-backed migration/constraint testing (§19, AC-QUALITY-2). | Stage-specific and final-chain Flyway startup remain Testcontainers-backed; H2 remains prohibited. | Preserved |
| Clean Compose startup applies migrations (AC-QUALITY-3). | Clean startup now applies the ordered three-file chain rather than one monolithic file; observable result is identical. | Preserved |
| Learning order is guidance, not a new product requirement (§21). | Just-in-time migrations improve story readiness without adding an endpoint, entity, behavior, or infrastructure component. | Preserved |
| Fixture funding is non-production (§19.4, AC-WALLET-3). | No funding migration or route is introduced. | Preserved |

No FR, BR, acceptance criterion, endpoint, entity, security boundary, transaction guarantee, or scope exclusion is weakened.

## Remaining good-spine checklist

| Dimension | Result | Notes |
|---|---|---|
| Real divergence points fixed | Pass with refinement | All initiative-level dimensions are controlled; AD-7 should internalize the exact migration partition/immutability rule already fixed elsewhere. |
| Every Rule prevents its stated divergence | Pass | AD-1..AD-6 and AD-8..AD-12 align directly; AD-7's current rule prevents front-loading in substance, though its `Prevents` text could name that risk more precisely. |
| Deferred hides no required choice | Pass | Deferred items remain post-MVP product/infrastructure concerns; migration behavior is not deferred. |
| Named technology fit/current | Pass | Official sources confirm Spring Boot 4.1.1, PostgreSQL 18.6, Maven 3.10.0, and Boot 4.1.1 management of Flyway 12.4.0, PostgreSQL JDBC 42.7.13, JUnit 6.0.3, and Testcontainers 2.0.5. |
| Brownfield ratification | N/A | Greenfield learning project. |
| Driving PRD coverage | Pass | FR-1..FR-14 and all acceptance groups remain represented. |
| Parent-spine compatibility | N/A | No inherited parent spine. |
| Initiative dimensions owned | Pass | Application shape, module boundaries, data, mutations, concurrency, security, API/errors, tests, migration/configuration, delivery, and future scope are decided or explicitly deferred. |
| Downstream usability | Pass | The companion and epics provide exact migration filenames, contents, order, and stage gates. |

Official version references checked on 2026-10-06:

- [Spring Boot 4.1.1 project/release](https://spring.io/projects/spring-boot)
- [Spring Boot 4.1 managed dependency coordinates](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html)
- [PostgreSQL 18.6 release notes](https://www.postgresql.org/docs/release/18.6/)
- [Maven release history](https://maven.apache.org/docs/history.html)

## Triage

### Critical

None.

### High

None.

### Medium

1. **Autofix AD-7:** promote the exact `V1`/`V2`/`V3` partition, no-foundation-domain-table rule, and append-only shared-migration rule into AD-7 itself.

### Low / administrative

1. The spine remains `status: draft`; that is appropriate while this post-change gate is running. Promote it back to `final` only after the parent finalization workflow resolves the medium refinement.

## Conclusion

**PASS WITH ONE MEDIUM REFINEMENT.** The just-in-time migration change is technically coherent, implementable, and fully non-regressive against the finalized PRD. The only remaining architecture-spine weakness is self-containment: AD-7 should carry the exact migration partition and append-only invariant that the solution design and epics already enforce.
