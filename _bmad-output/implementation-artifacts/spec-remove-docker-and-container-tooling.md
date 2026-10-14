---
title: 'Remove Docker and Container-Based Tooling'
type: 'chore'
created: '2026-10-12'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '1c3f7dae57bbe61598d99022ed5dd4630657f558'
context:
  - '{project-root}/_bmad-output/planning-artifacts/sprint-change-proposal-2026-10-12.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest no longer includes Docker, Docker Compose, Testcontainers, or container-based delivery/testing, but its active planning documents, Maven build, and six PostgreSQL integration-test classes still require them.

**Approach:** Remove container requirements and dependencies, retain every non-container MVP decision, and run database tests against a dedicated developer-managed `paynest_test` PostgreSQL database protected by a test-only database-name guard.

## Boundaries & Constraints

**Always:** Preserve one Spring Boot modular monolith, one runtime PostgreSQL database, three entities, seven endpoints, JWT, Flyway, Hibernate validation, real PostgreSQL integration evidence, atomic registration/Transfer behavior, future concurrency testing, OpenAPI, and local-only fixture funding. Update active source-of-truth documents before code. Preserve truthful historical verification records while marking their container approach superseded.

**Never:** Add H2, embedded databases, Docker artifacts, Testcontainers, a public funding endpoint, new product scope, new persisted entities, new endpoints, microservice infrastructure, or production-source test helpers. Never delete/reset data before confirming `select current_database()` equals exactly `paynest_test`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Local integration suite | PostgreSQL is reachable and current database is `paynest_test` | Flyway, JPA, HTTP, security, rollback, and constraint tests run without containers | Normal test failures remain visible |
| Unsafe database | Test datasource resolves to `paynest` or another database | No cleanup or schema reset occurs | Test aborts with a clear safety error |
| Migration contract rerun | Verified `paynest_test` contains prior schema/data | Only schema `paynest` is reset, then V1→V2 is verified | Reset is forbidden until the safety guard passes |
| Missing local PostgreSQL configuration | Server or credentials are unavailable | Build fails without silently selecting another database | Connection error identifies required test configuration |

</frozen-after-approval>

## Code Map

- `_bmad-output/planning-artifacts/prds/prd-Java-PayNest-2026-10-06/{prd.md,addendum.md}` — remove container scope and define direct-local quality evidence.
- `_bmad-output/planning-artifacts/architecture/architecture-Java-PayNest-2026-10-06/{solution-design.md,ARCHITECTURE-SPINE.md}` — replace Testcontainers/Docker decisions with dedicated PostgreSQL testing and local delivery.
- `_bmad-output/planning-artifacts/epics.md` — revise NFR/AR entries, Story 1.1/2.1 test wording, Epic 4, and Story 4.3.
- `_bmad-output/implementation-artifacts/{epic-1-context.md,spec-1-*.md}` — reconcile active guidance; append authoritative verification without falsifying historical runs.
- `pom.xml` — remove exactly three container-test dependencies.
- `src/test/resources/application-test.yml` — bind the dedicated test datasource from `PAYNEST_TEST_DB_*`.
- `src/test/java/com/paynest/support/TestDatabaseSupport.java` — new test-only guard and cleanup support; no production dependency.
- `src/test/java/com/paynest/{PayNestApplicationTests.java,MigrationContractTests.java}` — replace container provisioning; guard persistent-database writes/schema reset.
- `src/test/java/com/paynest/{auth,user,wallet}/*HttpIntegrationTests.java` — remove container wiring and guard existing row/trigger cleanup.
- Do not modify production Java, migrations, API contracts, entities, or historical review directories.

## Tasks & Acceptance

**Execution:**
- [x] Approved course-correction artifacts — mark the proposal approved; update PRD/addendum, architecture/spine, and epics without changing non-container requirements.
- [ ] Epic 1 context/specifications — replace normative container guidance and append superseding verification records after tests pass.
- [x] `pom.xml` and `application-test.yml` — remove container dependencies and configure externally supplied local test PostgreSQL.
- [x] `TestDatabaseSupport.java` and six integration suites — verify `paynest_test` before cleanup, preserve assertions, reset only the owned schema for staged migration testing, and remove all container APIs.
- [ ] Repository verification — run tests/local startup checks and search active artifacts for stale container dependencies or requirements.

**Acceptance Criteria:**
- Given the active build and source tree, when container references are inspected, then no Docker/Testcontainers dependency, annotation, import, runtime setting, Dockerfile, or Compose file remains.
- Given the dedicated test database, when the complete Maven suite runs, then existing Epic 1 behaviors and PostgreSQL-specific evidence pass without H2 or containers.
- Given an unsafe database name, when destructive test setup is attempted, then it aborts before changing data or schema.
- Given a clean local development database, when PayNest starts with the local profile, then Flyway migrates and Hibernate validates the unchanged schema.
- Given the revised planning artifacts, when scope is reviewed, then all non-Docker requirements remain unchanged and Story 4.3 covers direct local startup.

## Implementation Notes

- Removed the three container-test dependencies and all container annotations, imports, fields, and placeholder datasource overrides.
- Added one test-only utility that requires explicit `PAYNEST_TEST_DB_*` configuration, verifies `select current_database()` equals exactly `paynest_test`, guards row cleanup and schema reset, and emits clear missing-configuration failures.
- The staged migration contract now resets only the guarded `paynest` schema and uses the same environment-supplied datasource values as the Spring test profile.
- Local PostgreSQL 18 is running on port 5432 and Java 25 is available through the configured IntelliJ JDK, but this environment has no supplied test/development database credentials, so database-backed verification remains pending.

## Spec Change Log

- 2026-10-12: Implemented the no-container planning/build/test changes. Full PostgreSQL and direct-start verification remains pending explicit local credentials.

## Review Triage Log

## Design Notes

Use one small concrete test utility rather than a framework or hierarchy. Spring integration suites continue using their existing repositories, MockMvc, and cleanup order; migration testing obtains the same configured datasource values directly, guards the database, resets only `paynest`, and leaves the final V2 schema available.

## Verification

**Commands:**
- `.\mvnw.cmd test` — expected: complete suite passes against configured local `paynest_test`.
- `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` — expected: direct startup completes with Flyway and Hibernate validation.
- `rg -n -i "docker|compose|testcontainers|serviceconnection|postgresqlcontainer" pom.xml src _bmad-output/planning-artifacts _bmad-output/implementation-artifacts` — expected: no active requirement or code dependency remains; retained historical review evidence is explicitly non-authoritative.

**Results (2026-10-12):**
- `mvn.cmd -o "-Dtest=WalletTests,TestDatabaseSupportTests,EmailNormalizerTests,AuthenticationServiceTests,RegistrationServiceTests,PasswordEncoderTests,JwtServiceTests,JwtPropertiesTests,JwtAuthenticationFilterTests" test` with `JAVA_HOME` set to the installed Temurin 25.0.4.1 JDK — passed: 52 tests, 0 failures/errors/skips, including both database-safety/missing-configuration tests.
- `mvn.cmd -o "-Djava.version=21" dependency:tree "-Dscope=test" "-Dincludes=org.testcontainers:*,org.springframework.boot:spring-boot-testcontainers"` — passed with no matching dependency.
- Source/artifact search — passed for active code/build dependencies and container files; remaining terms are explicit exclusions, this course-correction record, or historical records marked non-authoritative.
- Full Maven suite — attempted and failed before database-backed execution because `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD` are not supplied. The required-configuration error names `PAYNEST_TEST_DB_URL`; no fallback database was selected.
- Direct local startup — not run because development database credentials and `PAYNEST_JWT_SECRET` are not supplied.
