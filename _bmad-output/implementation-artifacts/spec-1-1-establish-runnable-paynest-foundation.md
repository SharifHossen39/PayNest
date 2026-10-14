---
title: 'Story 1.1: Establish the Runnable PayNest Foundation'
type: 'feature'
created: '2026-10-06'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'NO_VCS'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest has finalized requirements and architecture but no application source, build, configuration, or tests. Later stories need a repeatable Spring Boot and PostgreSQL foundation that proves the selected stack works without prematurely introducing domain persistence.

**Approach:** Create one Java 25/Spring Boot 4.1.1 Maven application under `com.paynest`, configure PostgreSQL, Flyway, Hibernate validation, and safe externalized settings, then prove startup against PostgreSQL 18.6 using a dedicated developer-managed `paynest_test` database.

## Boundaries & Constraints

**Always:** Use the Maven 3.10.0 Wrapper; retain one Spring Boot deployment unit and one runtime PostgreSQL datasource; establish `auth`, `user`, `wallet`, `transfer`, `transaction`, and `common` package boundaries; let the Spring Boot BOM manage supported library versions; configure Flyway and Hibernate for the same PostgreSQL schema; use `ddl-auto=validate`; use a dedicated developer-managed `paynest_test` database for integration tests and guard destructive setup by exact database name; keep committed configuration free of real credentials and signing secrets.

**Never:** Add entities, repositories, controllers, product endpoints, or versioned domain migrations; create User, Wallet, or Transaction tables; use H2 or container tooling; introduce Lombok, MapStruct, Modulith, Actuator, messaging, caching, cloud, resilience, microservice, or code-generation infrastructure.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Clean build | Java 25 and a clean checkout | Maven Wrapper compiles the single application and runs tests | Build fails clearly if the required JDK is unavailable |
| Empty database startup | Empty PostgreSQL 18.6 database | Flyway initializes its history, Hibernate validates an empty domain model, and the application context starts | Connection or configuration failures fail startup without leaking secrets |
| Integration smoke test | `PAYNEST_TEST_DB_*` identifies the dedicated `paynest_test` database | Spring connects directly to real PostgreSQL; no H2 or container provisioning is used | Test fails clearly if configuration, PostgreSQL, Flyway, JPA, or the database-name guard fails |

</frozen-after-approval>

## Code Map

- `pom.xml` — new root build; pins Java/Spring Boot and contains only the architecture-approved dependency set.
- `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd` — new Maven 3.10.0 repeatable build entry points.
- `src/main/java/com/paynest/PayNestApplication.java` — sole Spring Boot application entry point.
- `src/main/java/com/paynest/{auth,user,wallet,transfer,transaction,common}/package-info.java` — durable feature-boundary declarations; no behavior or speculative layers yet.
- `src/main/resources/application.yml` — shared datasource, Flyway, schema, Hibernate validation, and safe environment-variable configuration.
- `src/main/resources/application-local.yml` — local-only non-secret defaults/placeholders; must not contain usable production credentials.
- `src/main/resources/db/migration/.gitkeep` — retains the migration location without consuming a Flyway version or creating a domain table.
- `src/test/java/com/paynest/PayNestApplicationTests.java` — PostgreSQL-backed application-context smoke test.
- `src/test/resources/application-test.yml` — isolated datasource settings bound from `PAYNEST_TEST_DB_*`.
- `_bmad-output/implementation-artifacts/epic-1-context.md` — authoritative distilled Epic 1 constraints; do not modify while implementing this story.

## Tasks & Acceptance

**Execution:**
- [x] `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd` — establish the Java 25, Spring Boot 4.1.1, and Maven 3.10.0 build with exactly the approved compile/runtime/test dependencies.
- [x] `src/main/java/com/paynest/PayNestApplication.java`, feature `package-info.java` files — create one runnable application and the six package-by-feature seams.
- [x] `src/main/resources/application.yml`, `application-local.yml`, `db/migration/.gitkeep` — configure externalized PostgreSQL/Flyway/JPA settings with no domain migration or real secret.
- [x] `src/test/java/com/paynest/PayNestApplicationTests.java`, `src/test/resources/application-test.yml` — add a real PostgreSQL 18.6 context-start test using the guarded dedicated `paynest_test` datasource.

**Acceptance Criteria:**
- Given Java 25 and a clean checkout, when the Maven Wrapper tests run, then the single Spring Boot 4.1.1 application under `com.paynest` compiles and starts with all six feature packages present.
- Given an empty PostgreSQL 18.6 database, when the context starts, then Flyway initializes before Hibernate, both target the same schema, Hibernate validates rather than mutates it, and no PayNest domain table or versioned migration exists.
- Given the effective Maven model, when dependencies are inspected, then only the approved Spring Web MVC, Validation, Data JPA, Security, Flyway/PostgreSQL, JJWT, springdoc, and test dependencies are present with BOM-managed versions where available.
- Given committed local and test configuration, when it is inspected, then all sensitive values come from environment variables or obvious non-secret placeholders and no real credential or JWT secret is present.
- Given the persistence smoke test, when it executes with `PAYNEST_TEST_DB_*` configured for `paynest_test`, then Spring Boot connects directly to PostgreSQL and the test passes without H2 or container provisioning.

## Implementation Notes

- Created the greenfield Maven/Spring Boot application and durable package seams without introducing domain types or operations.
- Kept Flyway and Hibernate on the `paynest` schema; the smoke test asserts that only `flyway_schema_history` exists.
- Corrected the implementation handoff's dependency drift by using `spring-boot-starter-flyway`, springdoc 3.1.1, and `spring-security-test` as selected by the architecture.
- Verified the Maven Wrapper and full test suite inside an Eclipse Temurin Java 25 container connected to Docker Desktop.
- Added line-ending and generated-file safeguards, strengthened the PostgreSQL smoke test to verify inherited schema/migration settings, and disabled only Spring Security's temporary generated-user auto-configuration until JWT security is implemented.
- Evaluated wrapper archive pinning and intentionally omitted `distributionSha256Sum`: the official script switches from ZIP to TAR.GZ when `unzip` is unavailable, so one archive-specific checksum breaks otherwise supported clean environments. HTTPS Maven Central plus the pinned distribution URL is retained for this learning foundation.

## Spec Change Log

- 2026-10-12: Replaced normative container-test guidance with the guarded dedicated `paynest_test` strategy. All earlier container execution and review references are retained solely as truthful historical, non-authoritative evidence.

## Review Triage Log

Entries below predate the 2026-10-12 no-container course correction. References to container-based execution are retained as truthful historical review evidence only and are not current implementation guidance.

| ID | Verdict | Evidence and route |
|---|---|---|
| B1 | maybe-false | The review diff lacks executable mode because no Git repository exists, but `./mvnw` passed in the Linux verification container. A committed Git mode is required to settle the clean-checkout claim; defer until version control is initialized. |
| B2 | low | `.gitattributes` is absent, so a future Windows Git checkout could rewrite the Unix wrapper to CRLF. This is a direct repository-hygiene patch. |
| B3 | medium | Initial patch attempted archive checksum pinning, but uncached Linux verification showed the official only-script wrapper switches from ZIP to TAR.GZ when `unzip` is unavailable; a single archive-specific checksum then rejects one supported path. Reclassified as rejected after verification: retain the pinned HTTPS Maven Central URL and wrapper portability. |
| B4 | medium | The verified startup log contains Spring Security's generated password. Patch the foundation to disable only the temporary generated-user auto-configuration until PayNest JWT security is implemented. |
| B5 | false | Base datasource fallbacks are explicit non-secret local placeholders permitted by the approved architecture; deploy profiles remain externally overrideable. No defect against this story. |
| B6 | false | The Java 25 verification log proves the application connected to the Testcontainers mapped JDBC URL, not the base localhost URL. `@ServiceConnection` binding is functioning. |
| B7 | medium | `application-test.yml` restates Flyway and Hibernate settings, allowing the shared configuration to regress unnoticed. Remove the duplicates and assert the effective setting. |
| B8 | medium | With no entities, startup alone cannot prove Flyway and Hibernate target the same schema. Add focused effective-property assertions. |
| B9 | medium | The table assertion does not detect a tableless versioned migration consuming V1. Assert zero non-null versions in Flyway history. |
| B10 | low | `.gitignore` is absent and generated `target/` output already exists. Add minimal Maven, IDE, environment, and local-secret exclusions. |
| V1 | medium | The verification reviewer independently confirmed that test-profile overrides mask the production Flyway/Hibernate switches. Patch with inherited settings and assertions. |
| V2 | medium | `@ServiceConnection` bypasses configured datasource credentials, so the `PAYNEST_DB_*` placeholder contract is not exercised. Add focused environment-resolution assertions without changing the actual container connection. |
| V3 | medium | No current assertion proves the Flyway history has zero applied versions. Patch the PostgreSQL smoke test. |
| V4 | low | The direct-dependency allowlist is not enforced by a custom build rule, but the transparent POM and required effective-model inspection satisfy this learning story. Reject an added policy framework as disproportionate complexity. |
| V5 | maybe-false | The Unix mode claim duplicates B1: no Git metadata exists, while the actual wrapper ran successfully on Linux. Defer until a repository records the executable bit. |
| E1 | maybe-false | The edge reviewer repeats the uncommitted Unix-mode risk. The future Git index is the missing evidence; defer with B1/V5. |
| E2 | medium | Same concern as B3. Reclassified as rejected after the uncached cross-environment check demonstrated that archive-specific pinning conflicts with the wrapper's built-in ZIP/TAR.GZ fallback. |
| E3 | low | An unset Unix `HOME` and `MAVEN_USER_HOME` is uncommon, and adding a project-local cache fallback would pollute the checkout. Reject as an unlikely upstream-wrapper edge. |
| E4 | low | A partially corrupted Unix wrapper cache can fail instead of self-repairing, but manual cache removal is straightforward and changing generated wrapper logic adds maintenance risk. Reject. |
| E5 | low | The Java fallback can fail poorly during authenticated download with missing credentials, but PayNest uses public Maven Central and normal curl/wget paths. Reject as unreachable in ordinary use. |
| E6 | low | An apostrophe in the Windows checkout path can confuse the generated wrapper's PowerShell bridge, but this rare upstream-wrapper edge does not justify custom quoting logic in the learning project. Reject. |
| E7 | low | A partially corrupted Windows wrapper cache can fail instead of self-repairing; manual cache removal is adequate and custom wrapper maintenance is disproportionate. Reject. |

## Design Notes

Use `package-info.java` because empty directories do not survive source control. The foundation intentionally allows Flyway’s infrastructure-owned schema-history table while reserving `V1__create_users.sql` for Story 1.2.

## Verification

**Commands:**
- `.\mvnw.cmd --version` — expected: Maven 3.10.0 running on Java 25.
- `.\mvnw.cmd help:effective-pom` — expected: a successful model containing only approved dependencies and managed versions.
- `.\mvnw.cmd test` — expected: unit/build checks and the guarded dedicated-PostgreSQL context smoke test pass.

**Historical environment note (superseded, non-authoritative):** The Windows host still reports Java 21. Docker Desktop was started, and the complete Wrapper test ran successfully inside an Eclipse Temurin Java 25 container against PostgreSQL 18.6.
