---
status: final
stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
  - step-03-create-stories
  - step-04-final-validation
inputDocuments:
  - prds/prd-Java-PayNest-2026-10-06/prd.md
  - prds/prd-Java-PayNest-2026-10-06/addendum.md
  - architecture/architecture-Java-PayNest-2026-10-06/solution-design.md
  - architecture/architecture-Java-PayNest-2026-10-06/ARCHITECTURE-SPINE.md
---

# Java-PayNest - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for Java-PayNest, decomposing the requirements from the finalized PRD and Architecture into implementable stories. PayNest has no UX design contract because the MVP is an API-only backend.

## Requirements Inventory

### Functional Requirements

FR1: An unauthenticated client can register a User with a validated name, normalized unique email, and password, receiving `201 Created` with a safe User profile and Wallet summary.

FR2: Every successfully registered User receives exactly one `0.00 USD` Wallet in the same atomic database operation; failure leaves neither User nor Wallet committed.

FR3: An unauthenticated client can sign in with valid credentials and receive a signed, expiring bearer JWT and expiry metadata; invalid credentials use one generic `401` response.

FR4: Every User, Wallet, Transfer, and Transaction operation requires a valid JWT, and the acting User is derived from the verified token rather than caller-supplied ownership identifiers.

FR5: An authenticated User can retrieve only their own safe profile through `GET /api/users/me`, with no credential or internal-security data exposed.

FR6: An authenticated User can retrieve their own exact Wallet balance, USD currency, and creation timestamp through `GET /api/wallet`; the endpoint accepts no User or Wallet ID.

FR7: An authenticated Sender can request a Transfer to an existing different User by normalized recipient email, using a positive, exact-to-cents amount within supported precision and available balance; rejected requests change no state.

FR8: A successful Transfer atomically debits the Sender Wallet, credits the Recipient Wallet, creates one Transaction, and returns `201 Created`; any pre-commit failure rolls back the complete operation.

FR9: Wallet updates use database-level concurrency protection so competing Transfers cannot overspend, lose updates, or commit a negative balance, and both Wallets are protected in deterministic order.

FR10: Each successful Transfer creates exactly one immutable `COMPLETED` Transaction containing Sender Wallet, Receiver Wallet, amount, status, and creation time; failed Transfers persist no Transaction.

FR11: An authenticated User can retrieve bounded, newest-first history containing only Transactions where their Wallet is Sender or Recipient, with viewer-relative `SENT`/`RECEIVED` direction and safe counterparty information.

FR12: An authenticated Sender or Recipient can retrieve safe Transaction detail; unknown and unrelated Transaction IDs return the same `404` response and expose no persistence graph.

FR13: Transaction history supports zero-based `page` and `size`, defaults to `0` and `20`, limits size to `100`, validates bounds, returns page metadata, and uses fixed `createdAt DESC, id DESC` ordering.

FR14: Generated OpenAPI/Swagger documentation describes all seven MVP operations, request and response schemas, validation constraints, success and representative error responses, and bearer-JWT security without real secrets.

### NonFunctional Requirements

NFR1: The MVP must remain one Maven-built Spring Boot application, one deployment unit, and one PostgreSQL database organized as a package-by-feature modular monolith.

NFR2: Controllers handle HTTP translation only, repositories handle focused persistence only, and application/domain logic owns business rules and complete business-operation orchestration.

NFR3: Implementation must favor simple, readable, concrete components and must not add abstractions, dependencies, infrastructure, endpoints, or entities solely for theoretical future extensibility.

NFR4: All monetary values use Java `BigDecimal` and PostgreSQL `NUMERIC(19,2)`; excess fractional precision is rejected rather than rounded, and floating-point money is prohibited.

NFR5: Registration and Transfer are local ACID operations whose escaping failures roll back every mutation, with rollback behavior proven by PostgreSQL-backed integration tests.

NFR6: Database foreign keys, unique constraints, `NOT NULL` constraints, and checks backstop critical invariants including unique normalized email, one Wallet per User, USD-only currency, non-negative balances, positive amounts, distinct participants, and completed-only status.

NFR7: The final Transfer implementation must prevent concurrent overspending and lost updates, reduce deadlock risk through deterministic lock ordering, bound lock waits, and roll back safely on residual conflicts.

NFR8: Known failures must map deterministically to the shared safe REST error envelope; responses must never expose stack traces, SQL, class names, raw persistence errors, passwords, hashes, JWT contents, or secrets.

NFR9: Passwords must use a modern adaptive one-way Spring Security encoder, must never be stored or logged in plaintext, and must accept the PRD's complete 8-72 Unicode-character contract without silent transformation.

NFR10: Authentication must be stateless and deny protected operations by default; JWT signing material and database credentials must be externalized and excluded from source control and logs.

NFR11: Object-level authorization must derive ownership from the authenticated User, use ownership-constrained queries where appropriate, return `404` for unrelated Transaction IDs, and never trust client-supplied owner identifiers.

NFR12: API responses must use dedicated DTOs rather than entities so password hashes, lazy persistence relationships, and recursive or oversized object graphs cannot leak.

NFR13: Persistence operations must be parameterized through JPA/repositories; wildcard CORS is not required, and deployed production traffic is expected to use HTTPS at an external deployment boundary.

NFR14: Transaction list requests must remain bounded by pagination, use participant/time indexes, and retrieve a 20-item page without loading the complete history or issuing an N+1 counterparty query pattern.

NFR15: Application logs must record safe startup, authentication outcome, Transfer outcome category, and unexpected-failure information at appropriate levels without sensitive request payloads; distributed tracing and metrics platforms are not required.

NFR16: Environment-specific values must use external configuration; the repository must document a clean local PostgreSQL setup and provide sample non-secret configuration.

NFR17: Unit tests must cover focused rules, while HTTP, security, JPA, Flyway, constraints, rollback, and concurrency behavior must be tested against real PostgreSQL rather than H2 where engine behavior matters.

NFR18: Testing must prioritize observable business behavior, security boundaries, atomicity, and concurrency rather than an arbitrary line-coverage percentage.

NFR19: Java timestamps use `Instant`, PostgreSQL uses `TIMESTAMPTZ`, external timestamps are ISO 8601 UTC instants, and time-dependent code remains deterministic in tests.

NFR20: The finished project must start directly against developer-managed PostgreSQL, applying Flyway migrations and passing Hibernate validation on a clean local database.

NFR21: The MVP must contain exactly three persisted domain entities and exactly seven product API endpoints, with no public Wallet-funding route or microservice infrastructure.

### Additional Requirements

AR1: Use Java 25 LTS, Spring Boot 4.1.1, Maven Wrapper 3.10.0, PostgreSQL 18.6, Boot-managed Hibernate/Flyway/JDBC/JUnit versions, JJWT 0.13.0, and springdoc-openapi 3.1.1 as the verified baseline.

AR2: Keep dependencies limited to Spring Web MVC, Validation, Data JPA, Security, Flyway, PostgreSQL, JJWT, springdoc, Spring Boot Test, and Security Test; do not add Lombok, MapStruct, Spring Modulith, Actuator, cloud, cache, messaging, resilience, container, or code-generation dependencies without a concrete requirement.

AR3: Use base package `com.paynest` with feature packages `auth`, `user`, `wallet`, `transfer`, `transaction`, and `common`; the `transfer` slice contains the use case and controller but no entity or repository.

AR4: Prefer concrete services. `RegistrationService`, `AuthenticationService`, `TransferService`, and focused query services represent meaningful use cases; helper services and interfaces are optional refactorings, not mandatory scaffolding.

AR5: Each feature declares its entity repository once. Focused atomic coordinators may directly use repositories owned by other feature modules when required for a readable local transaction.

AR6: Persist only `User`, `Wallet`, and `Transaction` with generated `BIGINT` identity IDs; Transfer remains a business operation rather than a persisted entity.

AR7: Map Wallet-to-User as a required lazy unidirectional owning `@OneToOne` using unique `wallets.user_id`; User has no inverse Wallet field.

AR8: Map Transaction-to-Wallet using two required lazy unidirectional `@ManyToOne` associations for Sender and Receiver; Wallet has no Transaction collections.

AR9: Configure no cascade delete or orphan removal. Registration explicitly persists User and Wallet, and all mapped associations remain lazy with explicit DTO fetching/projection.

AR10: Flyway owns schema evolution through just-in-time versioned SQL: `V1__create_users.sql` with the User mapping, `V2__create_wallets.sql` with the Wallet mapping, and `V3__create_transactions.sql` with the Transaction mapping and history indexes. Hibernate uses `ddl-auto=validate`, shared migrations are append-only, and production migrations contain no fixture funding.

AR11: Create named PostgreSQL constraints and the two history indexes `(sender_wallet_id, created_at DESC, id DESC)` and `(receiver_wallet_id, created_at DESC, id DESC)`.

AR12: `RegistrationService` owns one transaction containing normalization, PBKDF2 password encoding, User insert, and exactly one Wallet insert; the database unique constraint is authoritative for concurrent duplicates.

AR13: Use `DelegatingPasswordEncoder` with stored ID `{pbkdf2@SpringSecurity_v5_8}` and Spring Security's v5.8 PBKDF2 profile; verify multi-byte 72-character passwords without truncation.

AR14: Use JJWT HS256 access tokens with a Base64-encoded secret of at least 256 bits, `sub` containing User ID, `iat`, `exp`, optional fixed issuer, and a default externalized 60-minute lifetime; include no personal, role, Wallet, or balance claims.

AR15: Use a once-per-request JWT filter to validate the token, resolve the User, and place a feature-neutral immutable `AuthenticatedUser` in `SecurityContext`; the two Auth operations and documentation assets are public, while the other five product operations are protected.

AR16: `TransferService` is the required orchestration and transaction boundary for Sender/Recipient resolution, locked Wallet loading, rule validation, debit, credit, Transaction insertion, and response mapping.

AR17: The final concurrency strategy is PostgreSQL row-level pessimistic locking through JPA `PESSIMISTIC_WRITE`, protecting both Wallet rows in ascending Wallet-ID order under `READ COMMITTED` until commit.

AR18: Configure an externalized PostgreSQL session `lock_timeout`, defaulting to five seconds locally/in tests; do not retry automatically, map SQLSTATE `40P01`/`55P03` lock conflicts to `409 CONFLICT`, and map other unexpected Transfer persistence failures to `500 TRANSACTION_FAILED`.

AR19: Use the exact seven controller mappings and PRD request/response contracts; OpenAPI resources are documentation assets rather than additional product endpoints.

AR20: Use the stable error codes `VALIDATION_ERROR`, `AUTHENTICATION_FAILED`, `INVALID_TOKEN`, `ACCESS_DENIED`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND`, `TRANSACTION_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `CONFLICT`, `SELF_TRANSFER_NOT_ALLOWED`, `INSUFFICIENT_BALANCE`, `TRANSACTION_FAILED`, and `INTERNAL_ERROR` with a small meaningful unchecked exception set.

AR21: Use explicit request/response DTOs and manual mapping. Validate names, emails, passwords, exact monetary precision, pagination bounds, and positive Transaction IDs at the boundary, then recheck database-dependent rules in services while locks are held.

AR22: Use an injectable `Clock` for entity and token timestamps and configure application/database time behavior for UTC.

AR23: Use a dedicated developer-managed PostgreSQL database named exactly `paynest_test`, configured through `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD`, for persistence integration tests. Verify the database name before cleanup or schema reset; do not substitute H2 for migration, constraint, transaction, or locking evidence.

AR24: Run concurrency tests with separate threads, transactions, and database connections coordinated by a latch/barrier; do not wrap the test itself in one transaction, and assert committed database state after both operations finish.

AR25: Add a history query-count or equivalent SQL-shape assertion proving that one 20-item page does not trigger per-row counterparty queries.

AR26: Local demonstrations may use `scripts/dev/fund-wallet.sql`; automated tests may use SQL or test-source fixtures. Every funding mechanism must remain outside production routes and the production application classpath/profile.

AR27: Use springdoc to define one `bearerAuth` HTTP bearer/JWT scheme, document the seven operations and shared errors, mark only Auth endpoints public, and assert the generated specification contains exactly seven product paths.

AR28: Complete delivery through documented direct Spring Boot startup against developer-managed PostgreSQL. Docker, Docker Compose, Testcontainers, and other container-based delivery/testing are outside scope; externalize database and JWT secrets.

AR29: Follow the learning sequence: foundation, PostgreSQL/Flyway, User, Wallet relationship, atomic registration, Security, JWT, User/Wallet APIs, Transaction persistence, basic Transfer internals, rollback, concurrency problem, pessimistic locks plus public Transfer API, concurrency tests, history/detail, error hardening, OpenAPI, and direct local verification.

AR30: Treat microservice extraction, gateway, Kafka, Redis, service discovery, separate databases, domain events, outbox, sagas, distributed transactions, Kubernetes, and distributed observability as future theory only; none may shape MVP code or infrastructure.

### UX Design Requirements

Not applicable. PayNest is an API-only backend MVP, the PRD explicitly excludes a web or mobile UI, and no UX design contract was supplied.

### FR Coverage Map

FR1: Epic 1 - Register a validated User and return a safe account response.

FR2: Epic 1 - Create exactly one zero-USD Wallet atomically with registration.

FR3: Epic 1 - Sign in with credentials and receive an expiring JWT.

FR4: Epic 1 - Protect all non-Auth operations and derive identity from the verified JWT.

FR5: Epic 1 - Retrieve only the authenticated User's safe profile.

FR6: Epic 1 - Retrieve only the authenticated User's Wallet balance.

FR7: Epic 2 - Validate a Transfer to another registered User without changing state on rejection.

FR8: Epic 2 - Debit, credit, and record a Transfer as one atomic operation.

FR9: Epic 2 - Prevent concurrent overspending, negative balances, and lost updates.

FR10: Epic 2 - Persist exactly one immutable completed Transaction for each successful Transfer.

FR11: Epic 3 - Retrieve participant-only Transaction history with viewer-relative direction.

FR12: Epic 3 - Retrieve Transaction detail only for a participating User.

FR13: Epic 3 - Apply bounded, deterministic pagination to Transaction history.

FR14: Epic 4 - Publish complete OpenAPI documentation for the seven MVP operations.

## Epic List

### Epic 1: Secure Account and Wallet Access

A new User can register with securely stored credentials, receive exactly one Wallet atomically, sign in with JWT, and retrieve only their own profile and Wallet. This epic includes the minimum application, PostgreSQL, migration, and security foundation needed to deliver that complete account journey; it does not create a separate technical-foundation epic.

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6

**Implementation notes:** Establish the package-by-feature monolith, User and Wallet persistence, Flyway baseline, PBKDF2 password handling, registration transaction, Spring Security, JWT principal flow, DTO boundaries, and essential PostgreSQL-backed tests. Later epics can rely on authenticated User and Wallet ownership without changing this public behavior.

### Epic 2: Safe Money Transfer

An authenticated User with test/local fixture funding can send an exact USD amount to another registered PayNest User and receive a completed Transfer result, while invalid, failed, or competing requests leave balances and Transaction records consistent.

**FRs covered:** FR7, FR8, FR9, FR10

**Implementation notes:** Add the application-only Transfer use case, Transaction entity, exact-money rules, local ACID transaction boundary, completed-only record, rollback proof, ordered pessimistic Wallet locking, database constraints, and advanced PostgreSQL concurrency tests. Funding remains strictly test/local setup and never becomes a product endpoint.

### Epic 3: Private Transaction History and Detail

An authenticated User can review a bounded newest-first history of money they sent or received and inspect an individual Transaction when they participated, without learning anything about unrelated Transactions.

**FRs covered:** FR11, FR12, FR13

**Implementation notes:** Add participant-constrained queries, viewer-relative direction, safe counterparty DTOs, deterministic pagination, ownership-protected detail, history indexes, and query-shape verification. This epic reads Transactions created by Epic 2 and requires no future epic to provide its user outcome.

### Epic 4: Documented and Reproducible MVP Delivery

The learner/developer can discover all seven PayNest operations through accurate OpenAPI documentation and run the completed modular monolith reproducibly against developer-managed PostgreSQL, with stable errors, safe configuration, final integration evidence, and direct local delivery.

**FRs covered:** FR14

**Implementation notes:** Complete shared error hardening, safe logging/configuration, full contract verification, springdoc bearer documentation, the exactly-seven-path assertion, and clean direct-local setup guidance for separate development and test databases. This is the final delivery outcome, not an infrastructure expansion.

## Epic 1: Secure Account and Wallet Access

A User can register with securely stored credentials, receive exactly one Wallet atomically, sign in with JWT, and access only their own profile and Wallet.

### Story 1.1: Establish the Runnable PayNest Foundation

As a PayNest developer,
I want a runnable modular Spring Boot application with configured PostgreSQL and Flyway foundations,
So that each product capability can be implemented and tested on a consistent foundation.

**Acceptance Criteria:**

**Given** a clean project checkout with Java 25 available
**When** the Maven Wrapper build and application smoke test run
**Then** one Spring Boot 4.1.1 application under `com.paynest` starts successfully
**And** the initial package structure provides `auth`, `user`, `wallet`, `transfer`, `transaction`, and `common` feature boundaries without introducing separate deployables.

**Given** an empty PostgreSQL 18.6 database
**When** the foundation application starts before any entity story is implemented
**Then** datasource and Flyway connectivity initialize successfully against developer-managed PostgreSQL
**And** Hibernate is configured for validation rather than schema creation or update
**And** Flyway and Hibernate target the same configured PostgreSQL schema
**And** no versioned migration is consumed and no User, Wallet, or Transaction domain table is front-loaded by the foundation story.

**Given** the project dependency configuration
**When** the effective Maven dependencies are inspected
**Then** they contain only the approved Spring Web MVC, Validation, Data JPA, Security, Flyway, PostgreSQL, JWT, OpenAPI, and testing dependencies
**And** no microservice, messaging, cache, cloud, resilience, code-generation, or architectural-purity framework is introduced.

**Given** local or test configuration
**When** database or JWT settings are supplied
**Then** environment variables or non-secret placeholders provide those values
**And** no real credential or signing secret is committed to source control.

**Given** the integration-test profile
**When** the persistence smoke test runs
**Then** the configured datasource connects to the dedicated `paynest_test` PostgreSQL database
**And** Flyway initialization and application-context startup succeed without H2, container provisioning, or any versioned domain migration
**And** destructive setup is forbidden unless `select current_database()` returns exactly `paynest_test`.

### Story 1.2: Persist Users and Their One-to-One Wallets

As a PayNest developer,
I want User and Wallet persistence with an explicit one-to-one relationship,
So that each account has exactly one safe USD balance record for future registration and money movement.

**Acceptance Criteria:**

**Given** valid User data
**When** Story 1.2 introduces the User mapping
**Then** `V1__create_users.sql` creates only the `users` table and its named constraints
**And** the generated identifier is a `Long` backed by PostgreSQL `BIGINT`
**And** the trimmed name, normalized lowercase email, password hash, and UTC creation time are stored
**And** the entity exposes neither a plaintext password nor general public setters.

**Given** a database with only `V1__create_users.sql` applied
**When** the User-only application checkpoint starts and its repository tests run
**Then** Flyway validation and Hibernate schema validation pass
**And** PostgreSQL catalog assertions confirm `uk_users_email` exists with the definition in the solution design
**And** User persistence works before the Wallet mapping or `V2` migration exists.

**Given** emails that differ only by surrounding whitespace or letter case
**When** they are normalized and persisted
**Then** they resolve to the same normalized value
**And** the database unique constraint prevents more than one User from using that value.

**Given** a persisted User
**When** Story 1.2 introduces the Wallet mapping
**Then** `V2__create_wallets.sql` creates only the `wallets` table, its User foreign key, unique constraint, and Wallet checks
**And** a Wallet created for that User has a generated `Long` identifier, balance `0.00`, currency `USD`, and UTC creation time
**And** Wallet owns a required lazy unidirectional `@OneToOne` relationship through unique `wallets.user_id`
**And** User has no inverse Wallet field.

**Given** the same database is upgraded from `V1` to `V2`
**When** Flyway validation and PostgreSQL catalog assertions run
**Then** the previously applied `V1` checksum remains valid
**And** `fk_wallets_user`, `uk_wallets_user`, `ck_wallets_usd_currency`, and `ck_wallets_non_negative_balance` exist with the definitions in the solution design
**And** Hibernate validates both mapped entities against the upgraded schema.

**Given** Wallet monetary behavior
**When** valid exact-cent credit or debit operations are applied
**Then** arithmetic uses `BigDecimal`, preserves scale two, and compares values with `compareTo`
**And** no public balance setter, floating-point arithmetic, or silent rounding exists.

**Given** a non-positive, over-precise, or excessive debit
**When** Wallet balance behavior evaluates it
**Then** the operation is rejected before an invalid in-memory balance is accepted
**And** an insufficient debit cannot make the balance negative.

**Given** persisted User and Wallet records
**When** PostgreSQL-backed relationship tests execute
**Then** a second Wallet for the same User is rejected
**And** negative balance and non-USD values are rejected by database constraints
**And** no cascade delete or orphan removal is configured.

### Story 1.3: Register a User with an Atomic Wallet

As a prospective PayNest User,
I want to register with my name, email, and password,
So that I receive a secure account and exactly one Wallet without partial creation.

**Acceptance Criteria:**

**Given** a valid unique name, email, and password
**When** `POST /api/auth/signup` is submitted
**Then** the API returns `201 Created` with the PRD-defined User profile and `0.00 USD` Wallet summary
**And** the response contains no password, password hash, entity relationship, or security configuration.

**Given** registration input
**When** request validation runs
**Then** name is required, trimmed, and 2-100 characters
**And** email is required, syntactically valid, no longer than 254 characters, and normalized before lookup
**And** password is required, 8-72 characters, and is neither trimmed nor silently transformed
**And** invalid input returns `400 VALIDATION_ERROR` without attempting persistence.

**Given** a valid password, including a 72-character multibyte value
**When** registration processes the credential
**Then** it is encoded using the configured `{pbkdf2@SpringSecurity_v5_8}` profile
**And** only the encoded password is persisted
**And** changing the final password character produces a credential that does not authenticate against the original hash.

**Given** a valid registration request
**When** `RegistrationService` performs the business operation
**Then** normalized User creation and exactly one zero-USD Wallet creation occur inside one transaction
**And** User and Wallet are explicitly persisted without relying on cascade behavior
**And** sign-up does not automatically authenticate the new User.

**Given** Wallet persistence fails after the User insert
**When** the registration transaction exits with failure
**Then** the User insert is rolled back
**And** neither a User nor a Wallet remains committed.

**Given** an existing normalized email
**When** another registration uses an equivalent case or whitespace variation
**Then** the API returns `409 EMAIL_ALREADY_EXISTS`
**And** no duplicate User or Wallet is created.

**Given** two concurrent registrations for the same normalized email
**When** both pass any friendly pre-check
**Then** the database unique constraint remains authoritative
**And** at most one User and one Wallet commit
**And** the losing request returns the same safe `409 EMAIL_ALREADY_EXISTS` contract.

### Story 1.4: Sign In and Receive a JWT

As a registered PayNest User,
I want to sign in with my email and password,
So that I can authenticate subsequent requests with a time-limited bearer token.

**Acceptance Criteria:**

**Given** a registered User and matching password
**When** `POST /api/auth/signin` is submitted with valid credentials
**Then** the API returns `200 OK` with `accessToken`, `tokenType: "Bearer"`, and `expiresAt`
**And** the reported expiry matches the token's expiration claim.

**Given** sign-in input
**When** validation runs
**Then** email and password are required
**And** email must be syntactically valid and is normalized before lookup
**And** malformed input returns `400 VALIDATION_ERROR`.

**Given** valid credentials
**When** `AuthenticationService` authenticates the request
**Then** it verifies the raw password through the configured `PasswordEncoder`
**And** it issues an HS256 JWT whose `sub` is the decimal User ID and whose claims include `iat` and `exp`
**And** the token contains no password data, email, roles, Wallet ID, balance, or unnecessary personal information.

**Given** JWT configuration
**When** the application starts outside an explicit test profile
**Then** signing material is loaded from `PAYNEST_JWT_SECRET` as a Base64-encoded value of at least 256 bits
**And** missing or inadequate signing material causes a clear startup failure
**And** token lifetime is externally configurable through `PAYNEST_JWT_TTL`, with a default of 60 minutes.

**Given** an unknown normalized email or an incorrect password
**When** sign-in is attempted
**Then** both cases return the same `401 AUTHENTICATION_FAILED` error shape and generic message
**And** the response does not reveal whether the account exists
**And** logs contain neither the submitted password nor credential-sensitive details.

**Given** deterministic test time
**When** token issuance is tested through the injected `Clock`
**Then** issued-at and expiration claims are reproducible
**And** the token's signature, subject, and configured lifetime are verified.

**Given** the MVP authentication scope
**When** sign-in is implemented
**Then** no refresh token, logout endpoint, revocation list, role model, OAuth2 authorization server, session store, or token persistence is introduced.

### Story 1.5: Access the Current User Profile Securely

As an authenticated PayNest User,
I want to retrieve my own profile using my bearer token,
So that I can verify my identity without exposing credentials or another User's data.

**Acceptance Criteria:**

**Given** the Spring Security configuration
**When** requests enter the application
**Then** session management is stateless
**And** `POST /api/auth/signup`, `POST /api/auth/signin`, and documentation assets are public
**And** every other product endpoint requires authentication
**And** no roles or permission hierarchy is introduced.

**Given** a request with a valid `Authorization: Bearer <JWT>` header
**When** the JWT authentication filter processes it
**Then** the filter verifies the expected algorithm, signature, expiration, and numeric User subject
**And** resolves the persisted User represented by that subject
**And** places an immutable feature-neutral `AuthenticatedUser` containing the trusted User ID into `SecurityContext`.

**Given** a valid JWT for an existing User
**When** `GET /api/users/me` is requested
**Then** the API returns `200 OK` with only that User's ID, name, normalized email, and creation timestamp
**And** the controller passes the trusted principal ID to `UserQueryService`
**And** no password hash, token claim set, entity relationship, or other User data is serialized.

**Given** no bearer token or a malformed, tampered, incorrectly signed, or expired JWT
**When** a protected endpoint is requested
**Then** the request returns the shared `401 INVALID_TOKEN` response
**And** the response does not echo token contents or validation internals.

**Given** a structurally valid JWT whose subject no longer resolves to a User
**When** a protected endpoint is requested
**Then** authentication is rejected with the same safe `401 INVALID_TOKEN` contract
**And** no authenticated principal remains in `SecurityContext`.

**Given** a request for the current profile
**When** caller-controlled path, query, or body values are inspected
**Then** no User ID is accepted as an ownership selector
**And** identity is derived exclusively from the verified security principal.

**Given** security integration tests
**When** public and protected routes are exercised
**Then** sign-up and sign-in remain accessible without JWT
**And** the current-profile route succeeds only with a valid token
**And** missing, invalid, expired, tampered, and deleted-subject tokens are rejected.

### Story 1.6: View the Authenticated User's Wallet

As an authenticated PayNest User,
I want to view my Wallet balance and currency,
So that I can understand my available PayNest funds without accessing another User's Wallet.

**Acceptance Criteria:**

**Given** a valid JWT for a User with a Wallet
**When** `GET /api/wallet` is requested
**Then** the API returns `200 OK` with the exact two-decimal balance, `USD` currency, and Wallet creation timestamp
**And** the response follows the PRD-defined Wallet schema without exposing the User entity or persistence relationships.

**Given** an authenticated Wallet request
**When** `WalletController` delegates the operation
**Then** it passes only the trusted principal User ID to `WalletQueryService`
**And** the Wallet is queried by that User ID
**And** no User ID or Wallet ID is accepted through the path, query string, or request body.

**Given** two authenticated Users with different Wallets
**When** each calls `GET /api/wallet`
**Then** each receives only their own Wallet
**And** neither can select or infer the other User's balance by changing request input.

**Given** an authenticated User whose required Wallet is missing
**When** `GET /api/wallet` is requested
**Then** the API returns the safe `404 WALLET_NOT_FOUND` response
**And** the condition is logged as a data-integrity concern without exposing database or entity details.

**Given** exact monetary values such as `0.00`, `20.10`, and `500.00`
**When** Wallet responses are serialized
**Then** their decimal values remain exact and contain no floating-point artifacts.

**Given** the completed Wallet API
**When** the application's request mappings are inspected
**Then** no public route exists to create, delete, fund, deposit to, or withdraw from a Wallet
**And** local or test funding remains outside production-profile REST APIs.

**Given** PostgreSQL-backed security integration tests
**When** authenticated, anonymous, and invalid-token Wallet requests are exercised
**Then** the valid User receives only their own Wallet
**And** unauthorized requests receive `401` without Wallet data.

## Epic 2: Safe Money Transfer

An authenticated User with test/local fixture funding can send an exact USD amount to another registered PayNest User and receive a completed Transfer result, while invalid, failed, or competing requests leave balances and Transaction records consistent.

### Story 2.1: Persist Immutable Completed Transaction Records

As a PayNest developer,
I want successful money movements represented by immutable completed Transaction records,
So that later Transfer operations can produce a reliable history without introducing another business entity.

**Acceptance Criteria:**

**Given** two persisted Wallets
**When** Story 2.1 introduces the Transaction mapping
**Then** `V3__create_transactions.sql` creates only the `transactions` table, its named constraints, and the Sender and Receiver history indexes
**And** a Transaction created between the Wallets contains a generated `Long` ID, required Sender Wallet, required Receiver Wallet, exact `BigDecimal` amount, `COMPLETED` status, and UTC creation time
**And** the fields map to the existing PostgreSQL `transactions` table.

**Given** a database already migrated through `V2`
**When** it is upgraded to `V3` and Flyway validation runs
**Then** the `V1` and `V2` checksums remain valid
**And** PostgreSQL catalog assertions confirm `fk_transactions_sender_wallet`, `fk_transactions_receiver_wallet`, `ck_transactions_positive_amount`, `ck_transactions_distinct_wallets`, and `ck_transactions_completed_status`
**And** `idx_transactions_sender_history` and `idx_transactions_receiver_history` are intentionally created with the Transaction table so the later participant-history use case needs no rewrite of `V3`
**And** Hibernate validates all three entity mappings.

**Given** the Transaction entity relationships
**When** JPA mappings are inspected
**Then** `senderWallet` and `receiverWallet` are required lazy unidirectional `@ManyToOne` associations owned by Transaction
**And** Wallet has no outgoing or incoming Transaction collection
**And** no cascade delete or orphan removal is configured.

**Given** Transaction creation through application code
**When** a record is constructed
**Then** the supported creation path always selects `COMPLETED`
**And** amount, participants, status, and creation time have no public mutation path after creation
**And** no application update or delete operation exists.

**Given** an invalid Transaction with a non-positive amount, the same Sender and Receiver Wallet, an unsupported status, or a missing participant
**When** persistence is attempted
**Then** the named PostgreSQL foreign-key, check, or `NOT NULL` constraint rejects it
**And** no invalid Transaction is committed.

**Given** a valid Transaction
**When** it is persisted and loaded through `TransactionRepository`
**Then** its exact amount and participant references are preserved
**And** persistence tests execute against the dedicated `paynest_test` PostgreSQL database.

**Given** the MVP domain boundary
**When** the persistence model is inspected
**Then** `User`, `Wallet`, and `Transaction` remain the only persisted entities
**And** Transfer has no entity, table, or repository
**And** no `FAILED`, `PENDING`, `REVERSED`, or other Transaction state is introduced.

### Story 2.2: Implement and Validate the Atomic Transfer Use Case

As a PayNest developer,
I want the complete Transfer business operation implemented and verified behind an application boundary,
So that money movement is correct before it is exposed as a public endpoint.

**Acceptance Criteria:**

**Given** a fixture-funded Sender with `500.00` and a Recipient with `100.00`
**When** `TransferService` executes a service-level request for `150.00`
**Then** it returns a completed Transfer result
**And** the Sender balance becomes exactly `350.00`
**And** the Recipient balance becomes exactly `250.00`
**And** exactly one immutable `COMPLETED` Transaction is persisted.

**Given** a successful Transfer
**When** its application result is mapped
**Then** it contains the Transaction ID, direction `SENT`, Recipient name and email, exact amount, `USD`, `COMPLETED`, creation timestamp, and Sender `balanceAfter`
**And** no entity graph or internal Wallet identifier is exposed.

**Given** a Transfer request
**When** boundary validation runs
**Then** `recipientEmail` is required, syntactically valid, bounded to 254 characters, and normalized
**And** `amount` is required, positive, has at most two fractional digits, and fits `NUMERIC(19,2)`
**And** malformed input is rejected without changing state.

**Given** an authenticated Transfer request
**When** `TransferService` begins the use case
**Then** it derives the Sender exclusively from a trusted authenticated User ID supplied by the security boundary
**And** resolves the Recipient by normalized email
**And** raises the defined User-not-found failure for an unknown Recipient
**And** raises the defined self-Transfer failure for the same Sender and Recipient.

**Given** a valid Sender and Recipient
**When** the Sender has insufficient balance
**Then** the service raises the defined insufficient-balance failure
**And** neither balance changes
**And** no Transaction is persisted.

**Given** a valid non-concurrent Transfer
**When** `TransferService` executes inside its application transaction
**Then** it debits the Sender Wallet, credits the Recipient Wallet, and inserts the completed Transaction before commit
**And** all three mutations commit as one local PostgreSQL operation
**And** controllers contain no business rules and repositories do not orchestrate the use case.

**Given** a missing required Sender or Recipient Wallet
**When** the Transfer use case is attempted
**Then** the service raises the defined Wallet-not-found failure
**And** no state change commits.

**Given** local demonstrations or automated tests need a positive Sender balance
**When** test data is prepared
**Then** funding occurs only through SQL fixtures, test-source helpers, direct test JDBC setup, or the manual local-only script
**And** no production-profile REST funding endpoint or production migration is introduced.

**Given** unit and PostgreSQL-backed integration tests
**When** successful and rejected non-concurrent Transfers execute
**Then** exact balances, Transaction counts, application results, business failures, and unchanged failure-state assertions all pass
**And** no public `POST /api/wallet/transfers` mapping exists yet.

### Story 2.3: Prove Transfer Rollback and Safe Failure Behavior

As a PayNest User,
I want failed Transfers to leave both Wallets and Transaction history unchanged,
So that an unexpected error can never produce partial money movement.

**Acceptance Criteria:**

**Given** a funded Sender and an existing Recipient
**When** an unchecked failure is deliberately triggered after the Sender debit but before Recipient credit
**Then** the Transfer transaction rolls back
**And** both Wallet balances remain at their pre-request values
**And** no Transaction is persisted.

**Given** both in-memory Wallet mutations have occurred
**When** an unchecked failure is deliberately triggered before Transaction insertion or commit
**Then** both persisted balances roll back
**And** no Transaction is persisted.

**Given** Sender debit and Recipient credit have occurred
**When** Transaction insertion or flush fails because of a persistence error
**Then** both Wallet changes roll back with the failed insert
**And** the database contains no partial Transfer state.

**Given** a business-rule failure such as insufficient balance, unknown Recipient, self-Transfer, invalid amount, or missing Wallet
**When** the Transfer exits before commit
**Then** neither Wallet is modified
**And** no completed or failed Transaction record is created.

**Given** an exception that should cause rollback
**When** it leaves `TransferService`
**Then** the service does not catch and suppress it
**And** business failures escaping after mutation are unchecked or explicitly covered by the transaction rollback policy.

**Given** a failed Transfer attempt
**When** application logging occurs
**Then** a safe failure category is recorded for diagnosis
**And** no password, bearer token, password hash, signing secret, full sensitive request payload, or raw SQL detail is logged
**And** no separate transaction is opened to persist a `FAILED` record.

**Given** PostgreSQL-backed rollback integration tests
**When** committed state is queried after each forced failure
**Then** original balances and Transaction counts are unchanged
**And** the test evidence does not rely only on mocks or an in-memory database.

### Story 2.4: Expose and Protect Concurrent Wallet Transfers

As a PayNest User,
I want simultaneous Transfers to preserve the true available balance,
So that concurrent requests cannot overspend a Wallet or overwrite committed money movement.

**Acceptance Criteria:**

**Given** a Transfer between two Wallets
**When** `TransferService` obtains the mutable Wallet state
**Then** `WalletRepository` locks both rows using JPA `PESSIMISTIC_WRITE` under PostgreSQL `READ COMMITTED`
**And** the rows are requested in ascending Wallet-ID order regardless of Transfer direction
**And** Sender and Recipient are identified by their IDs rather than result-list position.

**Given** both Wallet locks are held
**When** Transfer rules are evaluated
**Then** self-Transfer, exact amount, currency, and available balance are rechecked under the locks
**And** the locks remain held until debit, credit, Transaction insertion, and commit or rollback finish.

**Given** a valid JWT and a Transfer protected by the ordered Wallet locks
**When** the Sender submits `POST /api/wallet/transfers`
**Then** the API returns `201 Created` with the PRD-defined completed Transaction summary and `balanceAfter`
**And** request validation and business failures map to their documented `400`, `404`, and `422` contracts
**And** the endpoint is not exposed before the locking behavior is active.

**Given** a Sender balance of `100.00` and two simultaneous requests to send `80.00` to different Recipients
**When** both requests begin through separate threads, transactions, and database connections
**Then** exactly one Transfer commits
**And** the other request is rejected for insufficient balance after observing the committed state
**And** the Sender balance finishes at exactly `20.00`
**And** total Recipient credits equal `80.00`
**And** exactly one new Transaction exists
**And** no Wallet balance is negative.

**Given** simultaneous opposite-direction Transfers between two Wallets
**When** both operations acquire their required locks
**Then** both follow the same ascending Wallet-ID order
**And** no committed update is lost
**And** residual database conflicts roll back safely.

**Given** the database connection configuration
**When** a Wallet row remains locked beyond `PAYNEST_DB_LOCK_TIMEOUT`
**Then** the wait is bounded, with a local/test default of five seconds
**And** no automatic retry occurs
**And** SQLSTATE `40P01` or `55P03` lock conflicts map to `409 CONFLICT` only after rollback.

**Given** an unexpected Transfer persistence failure
**When** the API renders the failure after rollback
**Then** it returns `500 TRANSACTION_FAILED` with a generic safe message
**And** the response contains no SQL, constraint text, driver details, class names, or stack trace.

**Given** a concurrent or lock-timeout integration test
**When** the test coordinates operations with a latch or barrier
**Then** it does not wrap both calls in one test transaction
**And** it applies a bounded test timeout
**And** it verifies committed database state after both operations finish.

**Given** application or locking defects attempt to produce a negative balance
**When** PostgreSQL evaluates the update
**Then** `CHECK (balance >= 0.00)` rejects the invalid state as a final defense
**And** the complete Transfer transaction rolls back.

**Given** the modular monolith deployment
**When** concurrency protection is inspected
**Then** it uses PostgreSQL row locks only
**And** no JVM lock, distributed lock, message broker, retry framework, saga, or distributed transaction mechanism is introduced.

## Epic 3: Private Transaction History and Detail

An authenticated User can review a bounded newest-first history of money they sent or received and inspect an individual Transaction when they participated, without learning anything about unrelated Transactions.

### Story 3.1: View Paginated Transaction History

As an authenticated PayNest User,
I want to view money I have sent and received in newest-first pages,
So that I can understand my Wallet activity without seeing another User's Transactions.

**Acceptance Criteria:**

**Given** a valid JWT for a User with sent and received Transactions
**When** `GET /api/transactions` is requested without pagination parameters
**Then** the API returns `200 OK` using `page=0` and `size=20`
**And** only Transactions where the User's Wallet is Sender or Receiver appear.

**Given** a Transaction where the authenticated User's Wallet is the Sender
**When** it is mapped into history
**Then** its direction is `SENT`
**And** the counterparty contains only the Recipient's name and email.

**Given** a Transaction where the authenticated User's Wallet is the Receiver
**When** it is mapped into history
**Then** its direction is `RECEIVED`
**And** the counterparty contains only the Sender's name and email.

**Given** each history item
**When** the response is serialized
**Then** it contains Transaction ID, viewer-relative direction, safe counterparty data, exact amount, `USD`, `COMPLETED`, and creation timestamp
**And** it exposes no Wallet ID, entity graph, password data, or unrelated User information.

**Given** multiple Transactions with overlapping creation times
**When** history is retrieved
**Then** results are ordered by `createdAt DESC` and then Transaction ID descending
**And** repeated requests produce deterministic ordering.

**Given** explicit valid pagination parameters
**When** `page >= 0` and `1 <= size <= 100` are requested
**Then** the response uses those bounds
**And** returns `page`, `size`, `totalElements`, `totalPages`, and `last` metadata
**And** empty and final pages report correct metadata.

**Given** a negative page, non-positive size, size above 100, or non-integer pagination value
**When** history is requested
**Then** the API returns `400 VALIDATION_ERROR`
**And** no unbounded fallback query runs.

**Given** an authenticated history request
**When** `TransactionQueryService` resolves ownership
**Then** it derives the current Wallet ID from the trusted principal User ID
**And** queries Transactions with a Sender-or-Receiver participant predicate
**And** accepts no caller-supplied User or Wallet identifier.

**Given** a history page containing 20 records
**When** PostgreSQL query behavior is measured
**Then** the request uses the participant history indexes and a bounded query plan
**And** it does not load the User's complete history into memory
**And** a query-count or equivalent SQL-shape assertion proves that counterparties do not cause an N+1 query pattern.

**Given** Transaction persistence mappings
**When** history is implemented
**Then** no outgoing or incoming Transaction collection is added to Wallet
**And** DTO projection or explicit fetching provides only the required response data.

### Story 3.2: View Participant-Protected Transaction Detail

As an authenticated PayNest User,
I want to inspect a Transaction in which I participated,
So that I can review its receipt details without exposing unrelated activity.

**Acceptance Criteria:**

**Given** a valid JWT and a Transaction where the authenticated User's Wallet is the Sender
**When** `GET /api/transactions/{id}` is requested
**Then** the API returns `200 OK`
**And** direction is `SENT`.

**Given** a valid JWT and a Transaction where the authenticated User's Wallet is the Receiver
**When** the same detail endpoint is requested
**Then** the API returns `200 OK`
**And** direction is `RECEIVED`.

**Given** an authorized Transaction detail response
**When** it is serialized
**Then** it contains Transaction ID, viewer-relative direction, Sender name and email, Recipient name and email, exact amount, `USD`, `COMPLETED`, and creation timestamp
**And** it contains no Wallet IDs, password data, lazy entity graph, or internal persistence metadata.

**Given** an authenticated detail request
**When** `TransactionQueryService` performs the lookup
**Then** it resolves the current Wallet ID from the trusted principal User ID
**And** executes an ownership-constrained query requiring that Wallet to equal the Transaction's Sender or Receiver
**And** does not authorize access using a caller-supplied User or Wallet ID.

**Given** an existing Transaction belonging only to other Users
**When** the authenticated User requests its ID
**Then** the API returns `404 TRANSACTION_NOT_FOUND`
**And** no Transaction or participant information is disclosed.

**Given** a Transaction ID that does not exist
**When** it is requested
**Then** the API returns the same `404 TRANSACTION_NOT_FOUND` status, error code, and safe message used for an unrelated Transaction
**And** the response does not reveal which condition occurred.

**Given** a non-positive, malformed, or unsupported Transaction ID
**When** the detail endpoint is called
**Then** the API returns `400 VALIDATION_ERROR`
**And** no unrestricted Transaction query is executed.

**Given** participant, unrelated-User, unknown-ID, anonymous, and invalid-token integration tests
**When** the endpoint is exercised
**Then** both participants receive the safe detail response
**And** unrelated and unknown IDs receive the indistinguishable `404` contract
**And** unauthenticated requests receive `401` without Transaction data.

## Epic 4: Documented and Reproducible MVP Delivery

The learner/developer can discover all seven PayNest operations through accurate OpenAPI documentation and run the completed modular monolith reproducibly against developer-managed PostgreSQL, with stable errors, safe configuration, final integration evidence, and direct local delivery.

### Story 4.1: Finalize Safe Errors, Logging, and Configuration

As a PayNest API consumer and developer,
I want predictable failures and safe runtime diagnostics,
So that I can understand problems without exposing credentials or internal implementation details.

**Acceptance Criteria:**

**Given** a known application failure
**When** `GlobalExceptionHandler` renders the response
**Then** it uses the shared structure containing `timestamp`, HTTP `status`, stable `error`, safe `message`, and request `path`
**And** validation failures may additionally contain a field-to-message `fieldErrors` object.

**Given** authentication or framework authorization fails before controller advice
**When** the security entry point or access-denied handler renders the response
**Then** it uses the same overall safe error structure
**And** invalid credentials map to `AUTHENTICATION_FAILED`
**And** missing, malformed, tampered, expired, or deleted-subject tokens map to `INVALID_TOKEN`.

**Given** known business and persistence failures
**When** they are mapped
**Then** the implementation uses only the documented codes `VALIDATION_ERROR`, `AUTHENTICATION_FAILED`, `INVALID_TOKEN`, `ACCESS_DENIED`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND`, `TRANSACTION_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `CONFLICT`, `SELF_TRANSFER_NOT_ALLOWED`, `INSUFFICIENT_BALANCE`, `TRANSACTION_FAILED`, and `INTERNAL_ERROR`
**And** their HTTP statuses match the PRD.

**Given** any client-visible error
**When** its payload is inspected
**Then** it contains no stack trace, SQL, driver message, constraint text, Java class name, entity internals, password, password hash, bearer token, JWT claims, signing secret, or database credential.

**Given** application startup, authentication attempts, Transfer outcomes, and unexpected exceptions
**When** logging occurs
**Then** appropriate safe event categories and diagnostic context are recorded
**And** full sensitive request payloads and secrets are excluded
**And** no correlation-ID platform, distributed tracing system, metrics platform, or complex observability dependency is introduced.

**Given** local or test runtime configuration
**When** environment-specific settings are supplied
**Then** database URL, username, password, JWT secret, token lifetime, and lock timeout are externally configurable
**And** committed configuration contains only safe defaults or obvious non-secret placeholders
**And** a sample environment file documents required variable names without containing real secrets.

**Given** global error-contract integration tests
**When** representative validation, authentication, authorization, not-found, conflict, business-rule, Transfer-failure, and unexpected-error cases execute
**Then** their status, stable code, response shape, and information-disclosure rules all pass
**And** the implementation uses a small meaningful exception set rather than one exception per endpoint or error code.

### Story 4.2: Publish and Verify the OpenAPI Contract

As a PayNest API consumer and learner,
I want generated documentation for every MVP operation,
So that I can understand, authenticate against, and test the backend without reading implementation code.

**Acceptance Criteria:**

**Given** the completed PayNest controllers and DTOs
**When** springdoc generates the OpenAPI document
**Then** it contains exactly these seven product paths and methods:

- `POST /api/auth/signup`
- `POST /api/auth/signin`
- `GET /api/users/me`
- `GET /api/wallet`
- `POST /api/wallet/transfers`
- `GET /api/transactions`
- `GET /api/transactions/{id}`

**And** Swagger/OpenAPI asset routes are treated as documentation resources rather than additional product operations.

**Given** the OpenAPI security configuration
**When** the generated components are inspected
**Then** they define `bearerAuth` as HTTP bearer authentication with JWT format
**And** sign-up and sign-in are documented as public
**And** the other five operations declare bearer authentication.

**Given** any documented operation
**When** its OpenAPI definition is inspected
**Then** it includes its request parameters or body, validation constraints, success status, success response schema, and representative shared error responses
**And** monetary fields preserve decimal semantics
**And** pagination defaults and bounds are documented.

**Given** the generated API schemas
**When** User, Wallet, Transfer, Transaction, page, token, and error models are inspected
**Then** they match the PRD response contracts
**And** persistence entities and internal relationships are not published as API schemas.

**Given** OpenAPI examples
**When** documentation is rendered
**Then** identities are invented and tokens are placeholders
**And** no real password, bearer token, JWT signing material, database credential, or sensitive production value appears.

**Given** the Swagger UI and OpenAPI document routes
**When** an unauthenticated learner accesses them
**Then** the documentation is available without JWT
**And** accessing documentation does not weaken authentication for protected product endpoints.

**Given** the OpenAPI contract integration test
**When** the generated document is parsed
**Then** it asserts the exact seven product paths and methods
**And** verifies the bearer scheme, public/protected distinction, validation metadata, shared error schema, and expected success statuses
**And** fails if an undocumented product endpoint is added.

### Story 4.3: Document and Verify Direct Local Startup

As a PayNest developer,
I want to start the completed application directly against developer-managed PostgreSQL,
So that I can reproduce the documented backend journey with explicit, safe local prerequisites.

**Acceptance Criteria:**

**Given** Java 25, the Maven Wrapper, PostgreSQL 18.6, and required non-secret local configuration
**When** the application starts directly with the local profile against a clean `paynest` database
**Then** the single PayNest application starts
**And** Flyway applies the versioned migrations
**And** Hibernate validates the resulting schema.

**Given** the running local application
**When** the documented smoke journey is followed
**Then** a User can register, sign in, retrieve their profile and Wallet, receive local fixture funding, complete a Transfer, and retrieve Transaction history and detail
**And** fixture funding uses only the documented manual local SQL mechanism
**And** no production-profile funding endpoint exists.

**Given** repository setup documentation
**When** a new learner follows it
**Then** it identifies prerequisites, development and dedicated test database creation, required environment variables, direct local startup, test execution, fixture funding, OpenAPI access, and shutdown steps
**And** a sample environment file contains only obvious placeholders
**And** real `.env` files remain excluded from source control.

**Given** the dedicated integration-test configuration
**When** the complete Maven suite runs
**Then** it connects to the developer-managed database named exactly `paynest_test`
**And** database-name safety checks run before cleanup or schema reset
**And** PostgreSQL-specific migration, constraint, transaction, rollback, security, HTTP, and concurrency evidence does not use H2 or containers.

**Given** the active build, source, configuration, and delivery artifacts
**When** their dependencies and runtime requirements are inspected
**Then** no Docker, Compose, Testcontainers, service-connection, image, or container dependency remains
**And** the system remains one Spring Boot deployment and one runtime PostgreSQL database with no added infrastructure.
