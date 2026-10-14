# Sprint Change Proposal: Remove Docker and Container-Based Tooling

**Project:** PayNest  
**Date:** 2026-10-12  
**Status:** Approved for implementation  
**Scope classification:** Moderate course correction  

## 1. Issue Summary

PayNest is a Java and Spring Boot backend learning project. The project direction has been refined so Docker and container-based tooling are no longer part of the MVP or its learning path. The intent is to remove Docker, Docker Compose, Testcontainers, and related settings while preserving every other product requirement, business rule, API contract, data model decision, security requirement, and quality goal.

No application Dockerfile or Compose definition has been implemented. The current impact consists of planned Docker delivery requirements and a Testcontainers-backed PostgreSQL integration-test setup.

Evidence found in the repository:

- The PRD requires a final Docker phase and Docker Compose acceptance evidence.
- The solution design requires Docker/Compose delivery and Testcontainers PostgreSQL tests.
- The epics include Docker-related requirements and Story 4.3 for Docker Compose delivery.
- `pom.xml` contains three Testcontainers dependencies.
- Six integration-test classes use Testcontainers, `PostgreSQLContainer`, or Spring Boot `@ServiceConnection`.
- Completed Epic 1 context/specification artifacts contain container-based test instructions and verification records.
- No Dockerfile, Compose file, or other application-container artifact currently exists.

## 2. Impact Analysis

### 2.1 Product and MVP impact

The business MVP is unchanged. The following remain binding:

- One Spring Boot modular-monolith application.
- One runtime PostgreSQL database.
- Exactly three persisted entities: User, Wallet, and Transaction.
- Exactly seven MVP endpoints.
- User-to-Wallet one-to-one and Transaction-to-Wallet many-to-one relationships.
- JWT authentication and ownership-based authorization.
- Atomic registration and atomic Transfer behavior.
- PostgreSQL row-level concurrency protection and deterministic lock ordering.
- Flyway migrations and Hibernate schema validation.
- PostgreSQL-backed integration, rollback, constraint, and concurrency testing.
- OpenAPI documentation.
- No public Wallet-funding endpoint or microservice infrastructure.

Only the delivery mechanism and test database provisioning mechanism change.

### 2.2 Epic and story impact

- Epic 1 remains completed functionally. Story 1.1 test-environment acceptance wording must change from Testcontainers to a dedicated local PostgreSQL test database.
- Epic 2 behavior remains unchanged. Future persistence and concurrency test criteria must refer to the dedicated PostgreSQL test database.
- Epic 3 is unaffected except that its PostgreSQL integration tests use the revised test environment.
- Epic 4 retains error hardening, OpenAPI, configuration, documentation, and final verification. Its Docker delivery outcome changes to direct local startup and setup verification.
- Story 4.3 is renamed and rewritten rather than replaced by a new product story.
- No new epic, product story, endpoint, entity, or infrastructure component is required.
- Epic sequencing remains unchanged.

### 2.3 Artifact impact

Active artifacts requiring changes:

1. Finalized PRD and its implementation-oriented addendum.
2. `solution-design.md` and `ARCHITECTURE-SPINE.md`.
3. `epics.md`.
4. Epic 1 context and completed Story 1.1–1.6 specifications where they prescribe or verify container tooling.
5. `pom.xml`.
6. `src/test/resources/application-test.yml`.
7. Six PostgreSQL integration-test classes.

Historical architecture/PRD review outputs remain review records rather than current requirements. They do not control implementation after this approved course correction.

### 2.4 Technical impact

The production application requires no business-code change. The integration-test environment changes from an automatically created PostgreSQL container to a developer-managed PostgreSQL database dedicated to automated tests.

Primary risk: tests could damage development data if pointed at the wrong database. The revised design therefore requires a small test-only database-name safety check before cleanup or schema-reset operations.

## 3. Recommended Approach

Use a direct adjustment to the existing plan. Do not roll back completed Epic 1 functionality and do not redefine the PayNest MVP.

### Rationale

- No Docker delivery artifacts exist, so removing future Docker work is inexpensive.
- Testcontainers is isolated to test dependencies and six test classes.
- Real PostgreSQL remains available locally, preserving database-specific learning and correctness evidence.
- Reusing existing epics avoids unnecessary backlog churn.
- The change reduces learning and environment complexity without changing product behavior.

### Assessment

| Dimension | Assessment |
|---|---|
| Product-scope impact | None |
| Documentation effort | Medium |
| Implementation effort | Medium |
| Product behavior risk | Low |
| Test-environment risk | Medium, controlled by database isolation and safety checks |
| Rollback required | No |
| New epic required | No |

## 4. Detailed Change Proposals

### 4.1 PRD

- Remove containerization as a learning outcome and delivery requirement.
- Explicitly place Docker, Docker Compose, Testcontainers, and container-based delivery/testing outside PayNest scope.
- Require direct Spring Boot startup against developer-managed PostgreSQL.
- End the learning sequence at OpenAPI and direct local verification.
- Remove the containerization phase.
- Replace Docker Compose acceptance with clean local PostgreSQL startup, Flyway migration, Hibernate validation, and application-start evidence.
- State that integration tests use a dedicated PostgreSQL test database and never the normal development database.
- Preserve all other requirements and acceptance criteria.

### 4.2 Architecture

- Remove Testcontainers from the technology baseline and Maven dependency design.
- Remove the Docker/Compose delivery architecture.
- Document direct local application and PostgreSQL prerequisites.
- Use `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD` for integration tests.
- Rewrite ADR-9 to select a dedicated developer-managed PostgreSQL test database.
- Continue rejecting H2 and mocks as evidence for PostgreSQL migrations, constraints, rollback, and locking.
- Require a test-only database safety check before destructive test setup.
- Update the learner roadmap and Definition of Done to remove container phases and image terminology.
- Reconcile the Architecture Spine with these decisions.

### 4.3 Epics and stories

- Update NFR20 and AR1, AR2, AR23, AR28, and AR29 to remove container requirements.
- Update Story 1.1 acceptance criteria to use the dedicated PostgreSQL test database.
- Update future persistence and concurrency stories to use separate connections and transactions against that database.
- Remove Compose wording from Story 4.1.
- Rename Story 4.3 to **Document and Verify Direct Local Startup**.
- Make Story 4.3 cover prerequisites, local databases, environment variables, Flyway/Hibernate startup, test execution, smoke verification, fixture funding, and shutdown.
- Add verification that active build/runtime artifacts contain no Docker or Testcontainers dependency.

### 4.4 Code and test configuration

Remove from `pom.xml`:

- `org.springframework.boot:spring-boot-testcontainers`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:testcontainers-postgresql`

Update `application-test.yml` with the dedicated test datasource configuration.

Remove Testcontainers imports, annotations, container fields, service connections, and obsolete placeholder datasource overrides from:

- `PayNestApplicationTests`
- `MigrationContractTests`
- `RegistrationHttpIntegrationTests`
- `SignInHttpIntegrationTests`
- `CurrentUserProfileHttpIntegrationTests`
- `CurrentWalletHttpIntegrationTests`

Keep the existing HTTP, security, Flyway, Hibernate, JPA, constraint, rollback, and business assertions.

`MigrationContractTests` may reset only the PayNest schema within the verified dedicated test database. No destructive test operation may run before the database safety check succeeds.

### 4.5 Completed implementation artifacts

- Reconcile `epic-1-context.md` and completed Story 1.1–1.6 specifications with the new test strategy.
- Do not rewrite historical verification as though local PostgreSQL had been used originally.
- After revised tests pass, record the new local PostgreSQL verification as authoritative and mark the former container approach as superseded.

## 5. Implementation Sequence

1. Update the PRD and addendum.
2. Update the solution design, ADR-9, and Architecture Spine.
3. Update epics and affected story acceptance criteria.
4. Provision/document the dedicated `paynest_test` database and test credentials.
5. Remove Testcontainers dependencies from Maven.
6. Update the test profile datasource.
7. Refactor the six affected integration-test classes.
8. Add the minimal test-database safety check.
9. Run the complete Maven test suite against local PostgreSQL.
10. Run the application directly with the local profile and verify Flyway/Hibernate startup.
11. Reconcile completed Epic 1 context/specification records with actual verification evidence.
12. Search active code, configuration, PRD, architecture, and epics for stale Docker/Testcontainers requirements.
13. Perform the final consistency audit.

## 6. Success Criteria

- No Dockerfile or Compose file exists.
- No Docker/Testcontainers dependency or integration remains in active code or build configuration.
- Tests connect only to a dedicated PostgreSQL test database.
- Safety checks prevent destructive test setup against the normal development database.
- The complete existing test suite passes against local PostgreSQL.
- Flyway migrations and Hibernate validation work during direct local startup.
- PRD, architecture, epics, and active implementation guidance agree on the no-Docker direction.
- All non-Docker PayNest requirements remain unchanged.
- The MVP remains one modular-monolith application with three entities and seven endpoints.

## 7. Handoff

**Classification:** Moderate — planning artifacts and test infrastructure change, but product behavior does not.

**Product/architecture responsibility:** Apply the approved edits to the PRD, architecture documents, and epics without expanding or reducing any other requirement.

**Developer responsibility:** Remove container-based test dependencies, migrate tests to the dedicated local PostgreSQL test database, add the safety guard, and verify the full suite.

**User responsibility:** Provide or approve credentials for a locally installed PostgreSQL server and permit creation of the dedicated test database before implementation verification.

## 8. Checklist Status

- [x] Trigger and problem identified.
- [x] Evidence collected.
- [x] Epic and story impact assessed.
- [x] PRD and architecture conflicts identified.
- [x] UI/UX impact marked not applicable.
- [x] Direct-adjustment path selected.
- [x] Rollback rejected as unnecessary.
- [x] MVP behavior confirmed unchanged.
- [x] Specific proposals reviewed incrementally and approved.
- [x] Complete Sprint Change Proposal approved for implementation.
- [x] Planning artifacts updated.
- [x] Code and test infrastructure updated.
- [ ] Verification completed.

## 9. Remaining Assumptions

- “Remove Docker completely” includes Testcontainers and any future Docker/Compose delivery work.
- A local PostgreSQL server is available or will be installed outside the project repository.
- `paynest_test` is reserved for automated tests and may have its PayNest-owned schema/data reset.
- Historical review reports are retained as review history; active source-of-truth documents and implementation guidance are updated.
- The finalized PRD remains the source of truth for every requirement unrelated to Docker or container-based tooling.
