# PayNest Incremental Migration Reality Review

**Review date:** 2026-10-06  
**Artifact reviewed:** `ARCHITECTURE-SPINE.md`, with the matching migration sections in `solution-design.md` and story checkpoints in `epics.md`  
**Focus:** Flyway-before-domain configuration, zero-entity Hibernate validation, and the technical coherence of `V1 users -> V2 wallets -> V3 transactions`

## Verdict

**PASS — the incremental Flyway decision is technically coherent, with no architecture must-fix.**

The foundation checkpoint can start against an empty PostgreSQL database with Flyway enabled and Hibernate set to `validate` before any domain entity or domain migration exists. Flyway can establish its own schema-history metadata without creating a PayNest domain table, and Hibernate has no mapped domain table to require at that checkpoint. Adding `V1`, `V2`, and `V3` later is a normal pending-migration sequence.

Two implementation conditions should be carried into Story 1.1 but do not require another architecture change:

1. Include the PostgreSQL-specific Flyway database module required by Spring Boot 4.1.x, not only the generic Flyway starter/integration.
2. Keep entity classes and Spring Data repositories out of the foundation checkpoint until their matching migration is introduced.

## Evidence and analysis

### 1. Flyway can be configured before domain migrations exist

**Result: Pass.**

Spring Boot 4.1.1 auto-configures Flyway and calls `Flyway.migrate()` during application startup. Its default migration location is `classpath:db/migration`. Boot 4.1.1 also defaults `spring.flyway.fail-on-missing-locations` to `false`, so a temporarily empty or absent migration location does not by itself make the foundation application invalid.

Flyway's documented empty-database behavior is compatible with this checkpoint: it creates `flyway_schema_history` when that metadata table does not yet exist, scans for migrations, and applies later pending migrations in version order. Therefore, a foundation startup with no `V*` scripts can be followed by a later startup containing `V1__create_users.sql`; `V1` is pending and is applied normally. No baseline migration and no `baselineOnMigrate` override are required for this greenfield database.

The acceptance criterion should continue to say that no **User, Wallet, or Transaction domain table** is front-loaded. It should not claim that PostgreSQL contains no tables whatsoever, because Flyway's own `flyway_schema_history` table is expected infrastructure metadata.

Spring Boot also detects Flyway as a database initializer and configures the JPA `EntityManagerFactory` to depend on database initialization. This gives the required startup order: Flyway migrates first, then Hibernate validates the resulting mapped schema.

### 2. Hibernate `validate` can coexist with no mapped domain entities

**Result: Pass, based on the behavior of Boot-managed Hibernate ORM 7.4.5.Final.**

Spring Boot 4.1.1 supports `spring.jpa.hibernate.ddl-auto=validate` and manages Hibernate ORM 7.4.5.Final. Hibernate's schema-tooling source explicitly handles metadata with no contributors, using "no entities" as its example. Its grouped and individual schema validators iterate the tables in Hibernate's mapping metadata and validate each mapped physical table. With no entity mapping, there is no PayNest table in that metadata to validate, so there is no missing-domain-table failure. An extra unmapped table such as `flyway_schema_history` is not a validation error because Hibernate validates expected mapped objects; it does not require the database to contain only mapped tables.

This foundation checkpoint stays valid only while it truly has no premature persistence type. A `JpaRepository<User, Long>` introduced before `User` is an entity would fail for a different reason (`User` is not a managed type); that would not be a Flyway/Hibernate-validation limitation. Story 1.1 should therefore create package boundaries and persistence configuration, but Story 1.2 should introduce the first entities and repositories together with their migrations, as currently written.

The existing Testcontainers context-start smoke test is the appropriate executable proof for the exact assembled dependency set. This review establishes ecosystem feasibility; the story test remains the regression guard.

### 3. `V1 users -> V2 wallets -> V3 transactions` is coherent in PostgreSQL

**Result: Pass.**

The dependency order is correct:

```text
V1 users
  id BIGINT primary key
       ^
       | V2 wallets.user_id BIGINT not null + unique foreign key
V2 wallets
  id BIGINT primary key
       ^                         ^
       | V3 sender_wallet_id     | V3 receiver_wallet_id
V3 transactions
```

- `V1` creates `users` and its primary key before anything references it.
- `V2` creates `wallets` after `users`, so `wallets.user_id` can reference `users.id`. `NOT NULL` makes every Wallet have an owner, and `UNIQUE` makes a User own at most one Wallet at the database level.
- `V3` creates `transactions` after `wallets`, so both required Wallet foreign keys resolve to an existing primary key. The two participant columns are type-compatible `BIGINT`s.
- The history indexes belong in `V3` because their table and columns first exist there.
- PostgreSQL foreign keys enforce referential integrity, while its unique constraint supplies the required uniqueness and associated unique index for `wallets.user_id`.

The database relationship intentionally does not prove that every User already has a Wallet; a foreign key points from Wallet to User, not vice versa. PayNest correctly enforces the stronger "registered User receives exactly one Wallet" invariant in the atomic registration transaction plus the Wallet uniqueness constraint. This is not a migration-order gap.

### 4. The staged JPA mappings line up with the staged schema

**Result: Pass, with a build-sequencing rule.**

The checkpoints are internally consistent:

| Checkpoint | Migrations present | Mapped entities present | Expected validation |
|---|---|---|---|
| Foundation | none | none | Starts; validates an empty domain metamodel |
| User/Wallet persistence | `V1`, `V2` | `User`, `Wallet` | Validates `users` and `wallets` after Flyway applies both |
| Transaction persistence | `V1`, `V2`, `V3` | `User`, `Wallet`, `Transaction` | Validates all three after Flyway applies `V3` |

Flyway orders versioned migrations numerically, so introducing `V1` and `V2` in the same story still applies them in the required order. The safe implementation rule is to land each new entity mapping in the same buildable change as its matching migration; do not merge an entity without the table it expects or delete/rename an already-applied migration. The spine and solution design already require append-only shared migrations.

Hibernate `validate` verifies mapped schema compatibility but is not a complete constraint/index verifier. The architecture already compensates correctly: PostgreSQL-backed integration tests must exercise named checks, unique constraints, foreign keys, and history indexes/query shape rather than treating successful Hibernate startup as proof of every database invariant.

## Implementation conditions and verification checklist

These are implementation details, not findings requiring the spine to be amended:

- Use Spring Boot's Flyway integration plus `org.flywaydb:flyway-database-postgresql`, as required by the Boot 4.1.1 documentation for PostgreSQL.
- Keep `spring.flyway.enabled=true` and `spring.jpa.hibernate.ddl-auto=validate`; do not enable Hibernate `create`, `update`, or `create-drop` in test as a shortcut.
- Leave `spring.flyway.baseline-on-migrate=false` for clean greenfield databases.
- At the foundation checkpoint, assert that application/JPA startup succeeds, Flyway is initialized, and `users`, `wallets`, and `transactions` do not exist. Do not fail merely because `flyway_schema_history` exists.
- At each entity checkpoint, start from a fresh Testcontainers PostgreSQL instance so the complete migration chain is proven from zero; optionally also test upgrading a database already at the prior version.
- Pair Java/JPA column metadata explicitly with the DDL contract (`BIGINT` identity, `NUMERIC(19,2)`, `TIMESTAMPTZ`, three-character currency, and string status) so Hibernate validation is deterministic rather than dependent on implicit length/type defaults.

## Findings summary

| Severity | Finding | Disposition |
|---|---|---|
| Critical | None | — |
| High | None | — |
| Medium | PostgreSQL requires the database-specific Flyway module under Spring Boot 4.1.x | Implementation condition; covered by the approved Flyway dependency category |
| Low | The foundation database may contain `flyway_schema_history` even though it contains no PayNest domain tables | Clarification only; current story wording already distinguishes domain tables |
| Low | A premature repository or entity would invalidate the zero-entity checkpoint | Preserve current story boundary |

## Final gate

**PASS.** The incremental-migration reconciliation is supported by the current Spring Boot, Flyway, Hibernate, and PostgreSQL behavior. `V1 users -> V2 wallets -> V3 transactions` is the correct dependency order, Flyway may safely be initialized before `V1` exists, and Hibernate `validate` can start at the foundation checkpoint with an empty mapped domain model.

## Primary official sources

- [Spring Boot 4.1.1 database initialization and Flyway startup](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Boot 4.1.1 Flyway application properties](https://docs.spring.io/spring-boot/appendix/application-properties/)
- [Spring Boot 4.1.1 managed dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Redgate Flyway: getting started with an empty database and pending migrations](https://documentation.red-gate.com/fd/getting-started-with-flyway-184127223.html)
- [Redgate Flyway `migrate` command](https://documentation.red-gate.com/flyway/reference/commands/migrate)
- [Hibernate ORM schema management with no entity contributors](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/tool/schema/spi/SchemaManagementToolCoordinator.java)
- [Hibernate ORM grouped schema validator](https://github.com/hibernate/hibernate-orm/blob/main/hibernate-core/src/main/java/org/hibernate/tool/schema/internal/GroupedSchemaValidatorImpl.java)
- [PostgreSQL 18 constraints and foreign keys](https://www.postgresql.org/docs/18/ddl-constraints.html)
