---
title: PayNest MVP Solution Design
status: final
created: 2026-10-06
updated: 2026-10-06
source: ../../prds/prd-Java-PayNest-2026-10-06/prd.md
spine: ARCHITECTURE-SPINE.md
---

# PayNest MVP Solution Design

## 1. Architecture outcome

PayNest will be implemented as one package-by-feature Spring Boot modular monolith backed by one PostgreSQL database. The design has three persisted entities (`User`, `Wallet`, and `Transaction`), seven HTTP endpoints, and two important local ACID business transactions: registration and transfer.

The principal implementation choice is a small, application-only `transfer` slice. It coordinates User lookup, Wallet locking and balance behavior, and Transaction persistence without becoming an entity or a fourth business domain. This avoids both a giant `WalletService` and speculative microservice abstractions.

No conflict with the finalized PRD was found. The design supplies implementation choices where the PRD intentionally left them open.

### 1.1 Quality priorities

In order:

1. Correct money and ownership behavior.
2. Code a learner can trace from HTTP to database.
3. Explicit, testable module responsibilities.
4. Small dependency and infrastructure footprint.
5. Future extraction visibility without future-distributed-system machinery.

### 1.2 System context

```text
API client
    |
    | HTTPS in a deployed environment; HTTP is acceptable locally
    v
+----------------------------------------------------------+
| One PayNest Spring Boot application                      |
|                                                          |
| auth | user | wallet | transfer | transaction | common  |
+-----------------------------+----------------------------+
                              |
                              | JDBC / local ACID transactions
                              v
                    +--------------------+
                    | One PostgreSQL DB  |
                    | users              |
                    | wallets            |
                    | transactions       |
                    +--------------------+
```

There is no network boundary among modules. A transfer is one method call graph and one database transaction, not a distributed workflow.

### 1.3 Implementation Complexity Guardrail

When implementing PayNest, prefer the simplest design that satisfies the PRD and teaches the intended backend concept.

Do not introduce:

- an interface without a concrete polymorphic or integration-boundary need;
- a service without meaningful business or orchestration responsibility;
- a repository abstraction over another repository;
- a framework merely for architectural purity;
- infrastructure for possible future microservices;
- additional persisted entities or product endpoints;
- distributed-system patterns in the monolith.

Concrete services are the default. A focused application use case may use the repositories declared by the relevant feature modules when that keeps one atomic flow easy to trace. Extract a separate service when it represents a reusable business capability, owns non-trivial rules, or prevents an existing service from becoming a god service. Do not extract a class merely to make mocking easier.

Class names in this document illustrate responsibility and placement unless a transaction boundary or public API contract explicitly depends on the named role. `RegistrationService`, `AuthenticationService`, `TransferService`, and the query services map directly to meaningful MVP use cases. Helpers such as `WalletTransferAccess` or `TransactionRecordingService` are optional refactorings, not day-one requirements.

If theoretical extensibility significantly reduces readability for this three-entity MVP, choose readability unless the PRD requires the added structure.

### 1.4 Implementation vs Learning

The final MVP must satisfy every PRD acceptance criterion, but the learner does not need to implement every hardening concern on day one.

| Topic | MVP implementation | Learning stage |
|---|---|---|
| Spring Boot foundation | Required | Early |
| JPA entities | Required | Early |
| User-Wallet one-to-one | Required | Early |
| Transaction-Wallet many-to-one | Required | Early |
| Basic transfer flow | Required | Early |
| `@Transactional` and ACID | Required | Early |
| Spring Security and JWT | Required | Early |
| Flyway | Required | Intermediate |
| Dedicated PostgreSQL persistence tests | Required | Intermediate |
| Pessimistic Wallet locking | Required for final Transfer correctness | Advanced hardening |
| Deterministic lock ordering | Required for final Transfer correctness | Advanced hardening |
| Concurrency and lock-timeout tests | Required before final acceptance | Advanced hardening |
| Query-count/N+1 verification | Required before final hardening | Advanced hardening |
| OpenAPI | Required | Later |
| Direct local startup verification | Required final delivery phase | Later |
| Container-based tooling | Not MVP | Excluded |
| Kafka, Redis/cache, microservices, saga/outbox | Not MVP | Future learning only |

Use these labels throughout this design:

- **MUST IMPLEMENT FOR MVP:** required product behavior or acceptance evidence.
- **IMPLEMENT LATER DURING LEARNING:** still needed before final MVP acceptance, but deliberately added after the basic flow works.
- **ARCHITECTURAL GUIDANCE ONLY:** a decision boundary or optional refactoring that should not create code until it earns its place.

### 1.5 How to use this design

- **Build path:** start with the learner roadmap in Section 17, then use Sections 3-14 as implementation reference for the active phase.
- **Decision reference:** use Section 15 for trade-offs and Section 18 as the MVP completion checklist.
- **Post-MVP context:** read Section 16 only when studying future service extraction; it does not change the build path.

## 2. Verified technology baseline

Versions were verified against primary project documentation on 2026-10-06. Patch upgrades within the selected release lines are acceptable after the test suite passes; changing a major version requires architecture review.

| Concern | Selection | Reason |
|---|---|---|
| Language | Java 25 LTS | Current Java LTS; records and modern language features are useful for DTOs while the domain remains conventional Java. |
| Framework | Spring Boot 4.1.1 | Current stable Spring Boot line and one coherent dependency BOM. |
| Build | Maven Wrapper using Maven 3.10.0 | Current Maven 3 GA and repeatable learner setup without requiring a global Maven version. |
| Database | PostgreSQL 18.6 | Current supported PostgreSQL major/patch for developer-managed local and dedicated test environments. |
| Persistence | Spring Data JPA and Boot-managed Hibernate | Directly supports the PRD's JPA relationship and transaction learning goals. |
| Migrations | Boot-managed Flyway 12.4.0 | One versioned SQL schema workflow aligned with the chosen Boot BOM. |
| JDBC driver | Boot-managed PostgreSQL JDBC 42.7.13 | Compatible driver supplied by the Boot dependency set. |
| JWT | JJWT 0.13.0 | Focused JWT creation and validation without introducing an authorization server. |
| API docs | springdoc-openapi 3.1.1 | Current security-fix patch with Spring Boot 4.1 support. |
| Test framework | Boot-managed JUnit Jupiter 6.0.3 | Default modern test foundation. |
| Database tests | Dedicated developer-managed `paynest_test` PostgreSQL database | Runs the real PostgreSQL engine for mappings, constraints, transactions, and locks without container tooling. |

Primary references: [Spring Boot project](https://spring.io/projects/spring-boot), [Spring Boot dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), [Oracle Java support roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html), [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/), [Apache Maven releases](https://maven.apache.org/docs/history.html), [JJWT releases](https://github.com/jwtk/jjwt/releases), and [springdoc releases](https://github.com/springdoc/springdoc-openapi/releases).

The project should inherit versions from Spring Boot wherever possible. Do not override Hibernate, Flyway, PostgreSQL JDBC, or JUnit merely to obtain a newer number; the managed set is the compatibility baseline.

### 2.1 Maven dependency set

Keep the `pom.xml` limited to dependencies that implement a stated requirement. Let the Spring Boot parent/BOM manage every version it provides.

| Scope | Maven coordinates |
|---|---|
| Compile | `org.springframework.boot:spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-flyway` |
| Runtime | `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` |
| JWT compile | `io.jsonwebtoken:jjwt-api:0.13.0` |
| JWT runtime | `io.jsonwebtoken:jjwt-impl:0.13.0`, `io.jsonwebtoken:jjwt-jackson:0.13.0` |
| OpenAPI compile | `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` |
| Test | `org.springframework.boot:spring-boot-starter-test`, `org.springframework.security:spring-security-test` |

Integration tests bind `spring.datasource.*` from `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD`. They must connect to the dedicated `paynest_test` database and run a test-only name guard before any row cleanup or schema reset. H2 and embedded database substitutes remain prohibited for PostgreSQL evidence.

Do not add Lombok, MapStruct, Spring Modulith, Actuator, Resilience4j, cache, messaging, cloud, or code-generation dependencies unless a later concrete requirement justifies them. Plain Java keeps the learning mechanics visible.

## 3. Application structure

Base package: `com.paynest`.

```text
src/main/java/com/paynest/
|-- PayNestApplication.java
|-- auth/
|   |-- api/
|   |   |-- AuthController.java
|   |   `-- dto/                    # SignUpRequest, SignUpResponse,
|   |                               # SignInRequest, TokenResponse
|   |-- application/
|   |   |-- AuthenticationService.java
|   |   `-- RegistrationService.java
|   `-- security/
|       |-- SecurityConfig.java
|       |-- JwtService.java
|       |-- JwtAuthenticationFilter.java
|       `-- JwtAuthenticationEntryPoint.java
|-- user/
|   |-- api/
|   |   |-- UserController.java
|   |   `-- dto/UserResponse.java
|   |-- application/
|   |   `-- UserQueryService.java
|   |-- domain/User.java
|   |-- persistence/UserRepository.java
|   `-- mapping/UserMapper.java
|-- wallet/
|   |-- api/
|   |   |-- WalletController.java
|   |   `-- dto/WalletResponse.java
|   |-- application/WalletQueryService.java
|   |-- domain/Wallet.java
|   |-- persistence/WalletRepository.java
|   `-- mapping/WalletMapper.java
|-- transfer/                       # use-case slice; no entity or repository
|   |-- api/
|   |   |-- TransferController.java
|   |   `-- dto/
|   |       |-- TransferRequest.java
|   |       `-- TransferResponse.java
|   `-- application/TransferService.java
|-- transaction/
|   |-- api/
|   |   |-- TransactionController.java
|   |   `-- dto/
|   |       |-- TransactionSummaryResponse.java
|   |       |-- TransactionDetailResponse.java
|   |       |-- CounterpartyResponse.java
|   |       `-- TransactionPageResponse.java
|   |-- application/TransactionQueryService.java
|   |-- domain/
|   |   |-- Transaction.java
|   |   `-- TransactionStatus.java
|   |-- persistence/TransactionRepository.java
|   `-- mapping/TransactionMapper.java
`-- common/
|   |-- config/
|   |   |-- ClockConfig.java
|   |   `-- OpenApiConfig.java
|   |-- security/AuthenticatedUser.java
|   `-- error/
|       |-- ApiError.java
|       |-- GlobalExceptionHandler.java
|       `-- exception/              # small stable business exception set

src/main/resources/
|-- application.yml
|-- application-local.yml
`-- db/migration/
    |-- V1__create_users.sql
    |-- V2__create_wallets.sql
    `-- V3__create_transactions.sql

src/test/java/com/paynest/
|-- unit/                            # fast domain and focused service tests
|-- integration/                     # HTTP, security, JPA, rollback tests
`-- concurrency/                     # real PostgreSQL competing-transfer tests

src/test/resources/
|-- application-test.yml
`-- fixtures/                        # test-only SQL data, including funding

scripts/dev/
`-- fund-wallet.sql                  # optional manual local-only helper
```

This is a practical starting tree, not a mandatory class inventory. Use concrete services. Tests can mock concrete collaborators or favor integration tests; mockability alone does not justify an interface. Add an interface only for real alternative implementations or a genuine external boundary.

### 3.1 Layer responsibilities

| Layer/package | Responsibility | Must not do |
|---|---|---|
| `api` | HTTP mapping, DTO validation, status codes, delegation | Business decisions, repository access, entity serialization |
| `application` | Use-case orchestration and transaction boundaries | HTTP concerns, raw response writing, speculative abstraction |
| `domain` | Entity invariants and state transitions such as debit/credit | Security-context lookup, HTTP status selection |
| `persistence` | Focused JPA queries declared by the owning feature | Business orchestration or duplicate repository wrappers |
| `mapping` | Explicit entity/query-result to response DTO conversion | Lazy traversal outside an active query plan |
| `security` | Authentication, token lifecycle, security failures | Wallet/Transaction business rules |
| `common` | Truly shared error/config primitives | Domain models, catch-all utilities, feature-specific rules |

### 3.2 Dependency direction

```text
auth.api ----------> auth.application.RegistrationService
auth.api ----------> auth.application.AuthenticationService
auth.application/security ---> user.persistence.UserRepository
auth.application.RegistrationService ---> wallet.persistence.WalletRepository
auth.security -----> common.security.AuthenticatedUser

user.api ----------> user.application
wallet.api --------> wallet.application
transfer.api ------> transfer.application
transaction.api ---> transaction.application

transfer.application.TransferService
    |---> user.persistence.UserRepository
    |---> wallet.persistence.WalletRepository
    `---> transaction.persistence.TransactionRepository

transaction.application.TransactionQueryService
    |---> wallet.persistence.WalletRepository # trusted User ID -> Wallet ID
    `---> transaction.persistence.TransactionRepository

transaction.domain ---> wallet.domain          # two required ManyToOne refs
wallet.domain ------> user.domain               # owning OneToOne reference
common ------------> no feature module
```

Each repository is declared once inside its owning feature.

Focused application coordinators may depend on those repositories directly when one local transaction spans features. Auth-owned Registration uses User and Wallet persistence, Transfer uses User, Wallet, and Transaction persistence, and Transaction queries use Wallet identity plus Transaction persistence.

Registration lives in Auth so dependencies remain acyclic: Auth depends on Wallet and User; Wallet depends on User for its entity mapping; User does not depend on Wallet. These cross-feature use cases do not transfer repository ownership.

If one of these coordinators becomes difficult to read or the same capability gains a second consumer, extract a concrete helper then. For example, an ordered two-Wallet lock query may later move behind `WalletTransferAccess`, or Transaction creation behind `TransactionRecordingService`; neither class is required initially. Spring Modulith, architecture-framework annotations, and interfaces for every service are not required.

## 4. Domain and entity design

### 4.1 User

| Field | Java/JPA design | Database rule |
|---|---|---|
| `id` | `Long`, generated identity, immutable after persistence | `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` |
| `name` | Required `String`, store trimmed value | `VARCHAR(100) NOT NULL` |
| `email` | Required normalized `String`; trim then lowercase with locale-independent rules | `VARCHAR(254) NOT NULL UNIQUE` |
| `passwordHash` | Required `String`; never exposed through a getter used by mapping | `VARCHAR(255) NOT NULL` |
| `createdAt` | Required `Instant`, assigned at creation through an injected `Clock` | `TIMESTAMPTZ NOT NULL` |

User exposes no method that returns a plaintext password and no general public setters. A small factory/constructor creates a valid normalized User. It intentionally has no inverse `wallet` field; Wallet-to-User is a unidirectional one-to-one, and use cases load a Wallet by User ID. This avoids relying on Hibernate bytecode enhancement to make an inverse one-to-one truly lazy.

### 4.2 Wallet

| Field | Java/JPA design | Database rule |
|---|---|---|
| `id` | `Long`, generated identity | `BIGINT ... PRIMARY KEY` |
| `user` | Owning `@OneToOne(fetch = LAZY, optional = false)` with a unique join column | `user_id BIGINT NOT NULL UNIQUE REFERENCES users(id)` |
| `balance` | `BigDecimal`, precision 19, scale 2 | `NUMERIC(19,2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0)` |
| `currency` | Stored `String` or enum value; only `USD` | `CHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD')` |
| `createdAt` | Required `Instant` | `TIMESTAMPTZ NOT NULL` |

Wallet encapsulates balance changes:

- `debit(amount)` rejects non-positive, over-precise, and insufficient amounts, then subtracts exactly.
- `credit(amount)` rejects non-positive or over-precise amounts, then adds exactly.
- Both preserve scale 2 and compare monetary values with `compareTo`.
- Money never uses `double`, implicit rounding, or scale-sensitive `equals` for numeric ordering.

There is no public balance setter. JPA dirty checking writes changes when the transfer transaction commits.

### 4.3 Transaction

| Field | Java/JPA design | Database rule |
|---|---|---|
| `id` | `Long`, generated identity | `BIGINT ... PRIMARY KEY` |
| `senderWallet` | Required `@ManyToOne(fetch = LAZY)`; owning join | `sender_wallet_id BIGINT NOT NULL REFERENCES wallets(id)` |
| `receiverWallet` | Required `@ManyToOne(fetch = LAZY)`; owning join | `receiver_wallet_id BIGINT NOT NULL REFERENCES wallets(id)` |
| `amount` | Required `BigDecimal`, precision 19, scale 2 | `NUMERIC(19,2) NOT NULL CHECK (amount > 0)` |
| `status` | Required enum stored as text; factory always chooses `COMPLETED` | `VARCHAR(20) NOT NULL CHECK (status = 'COMPLETED')` |
| `createdAt` | Required `Instant` | `TIMESTAMPTZ NOT NULL` |

The entity has a persistence constructor plus one creation path for a completed transfer. Business fields have no public setters and join/scalar columns are treated as non-updatable. The application has no Transaction update or delete use case.

### 4.4 Relationship choices

```text
User 1 <---- 1 Wallet (unidirectional owner: wallets.user_id)

Wallet 1 <---- N Transaction.senderWallet   (Transaction owns FK)
Wallet 1 <---- N Transaction.receiverWallet (Transaction owns FK)
```

- The unidirectional Wallet-to-User `@OneToOne` plus unique FK teaches the owning side directly. The conceptual inverse is expressed by `WalletRepository.findByUserId`, not a `User.wallet` field.
- Transaction-to-Wallet references are unidirectional from Transaction. The conceptual Wallet one-to-many relationships are queried through `TransactionRepository`; Wallet deliberately has no `outgoingTransactions` or `incomingTransactions` collection.
- All mapped associations are lazy. History/detail queries explicitly fetch/project the participant and counterparty data required by DTO mapping, preventing N+1 queries and lazy access during serialization.
- No cascade or orphan removal is configured. Registration explicitly saves User then Wallet inside one transaction, making the atomic behavior visible. There is no deletion feature, so cascade delete would add risk without value.
- Foreign keys use restrictive default deletion behavior. The application does not delete Users, Wallets, or Transactions.

## 5. Business transaction design

### 5.1 Atomic registration

`auth.application.RegistrationService.register` is the transaction boundary because it represents the complete business operation. This is **MUST IMPLEMENT FOR MVP**.

```text
AuthController
  -> validate SignUpRequest
  -> RegistrationService [BEGIN]
       -> normalize name/email
       -> pre-check email for friendly 409
       -> PBKDF2 hash password
       -> UserRepository inserts User
       -> WalletRepository inserts Wallet(user, 0.00, USD)
       -> flush when needed to surface constraints
       -> map safe response
     [COMMIT]
```

The database unique constraint, not the pre-check, is authoritative for concurrent duplicate registrations. A unique-constraint exception is translated to `409 EMAIL_ALREADY_EXISTS`. Any Wallet failure propagates and rolls back the User insert.

Directly using the User and Wallet repositories is appropriate here. `RegistrationService` is a small coordinator with one clear atomic responsibility. Extract a concrete Wallet-provisioning helper later only if Wallet creation gains meaningful rules or another caller. Do not create an interface for that hypothetical future.

### 5.2 Transfer request flow

`TransferService` is the main business use case and its transaction boundary is **MUST IMPLEMENT FOR MVP**.

```text
POST /api/wallet/transfers
    |
    v
TransferController
    | DTO validation; extract common AuthenticatedUser.userId
    v
TransferService.transfer(authenticatedUserId, request) [BEGIN]
    |-- UserRepository resolves sender and normalized recipient
    |-- reject missing recipient and self-transfer
    |-- WalletRepository obtains both Wallets
    |-- final hardening: lock both Wallets in ascending ID order
    |-- identify sender/receiver from IDs, not result-list position
    |-- revalidate amount and sufficient funds under the locks
    |-- senderWallet.debit(amount)
    |-- receiverWallet.credit(amount)
    |-- TransactionRepository inserts one COMPLETED Transaction
    |-- build TransferResponse
    v
[COMMIT] -> 201 Created
```

The transaction boundary is on the application use case, not the controller, entity, or individual repository call. This guarantees that locks remain held until both balance updates and the Transaction insert either commit or roll back.

The service must not catch and suppress failures that should roll back. Any business exception that can escape after mutation must be unchecked; Spring persistence exceptions already are. Translate a checked exception to an unchecked business exception or add it explicitly to `rollbackFor`. A forced unchecked failure after debit must leave no committed mutation.

`WalletTransferAccess` and `TransactionRecordingService` are optional refactorings. Start with the three owning repositories injected into `TransferService`; extract a helper only if the orchestration stops being easy to follow, a capability is reused, or isolation of complex persistence behavior materially improves readability.

## 6. Concurrency design

### 6.1 Progressive learning sequence

Concurrency correctness is required for the final MVP, but it should be learned after the basic transfer is visible and tested.

1. Implement a non-concurrent happy-path transfer in `TransferService`.
2. Put the whole use case inside one Spring transaction.
3. Verify rollback and understand the local ACID guarantee.
4. Reproduce or reason through two requests attempting to overspend the same Wallet.
5. Learn PostgreSQL row locks and JPA `PESSIMISTIC_WRITE`.
6. Change the Wallet repository query to lock both rows.
7. Acquire the two locks in deterministic ascending Wallet-ID order.
8. Add PostgreSQL-backed concurrency and lock-behavior tests.

The unlocked basic transfer is a learning checkpoint, not a complete MVP. Do not call the Transfer feature finished until the selected locking strategy and concurrency tests pass.

### 6.2 Final selected strategy: pessimistic row locking

`WalletRepository` exposes a focused query that selects the two requested rows in ascending ID order with JPA `PESSIMISTIC_WRITE`. With Hibernate/PostgreSQL this produces row-level `FOR UPDATE` behavior. The query and transaction run at PostgreSQL's default `READ COMMITTED` isolation.

Required ordering rule:

```text
lock(min(senderWalletId, receiverWalletId))
lock(max(senderWalletId, receiverWalletId))
```

The same rule applies to A-to-B and B-to-A transfers. It reduces cyclic waits because every transfer tries to acquire shared resources in the same global order.

Once locked, the service checks the Sender balance and performs both mutations. A competing transfer waits for the first transaction to finish, then validates against the resulting committed balance. This prevents both concurrent overspending and lost updates.

Keep the transaction open until the Sender debit, Recipient credit, and Transaction insert complete. Do not release locks between those steps and do not introduce a distributed lock.

### 6.3 Advanced lock hardening

This subsection is **IMPLEMENT LATER DURING LEARNING**, after pessimistic locking works. These steps are required before final MVP acceptance.

- Configure a finite PostgreSQL session `lock_timeout`, initially five seconds for local/test and externalizable by environment.
- Do not add automatic retries.
- Map Spring lock-acquisition failures backed by PostgreSQL SQLSTATE `40P01` (deadlock) or `55P03` (lock unavailable/timeout) to `409 CONFLICT` after rollback.
- Map other unexpected Transfer data-access failures to `500 TRANSACTION_FAILED` without exposing SQL or driver details.
- Test the chosen wait/timeout behavior with two real PostgreSQL connections.

These details belong in configuration, exception mapping, and advanced integration tests. They do not require a lock framework, retry library, or separate locking service.

### 6.4 Why this strategy

| Option | Assessment |
|---|---|
| Pessimistic row locks | Selected. It directly models “protect two balances, then decide,” makes the locking behavior visible in SQL, and needs no retry algorithm for the normal conflict path. |
| Optimistic `@Version` | Rejected for MVP. Correct only with carefully designed retries or exposed conflicts; retrying a multi-row financial operation adds complexity and can obscure the learning goal. |
| Atomic conditional debit SQL | Correct for the debit row but requires separate coordination of recipient credit and record insertion. It reduces domain/JPA learning value and still needs a transaction. |
| Serializable isolation | Rejected. Broader and more failure-prone than needed for two known rows; it would require retry handling. |
| JVM/distributed locks | Rejected. JVM locks do not protect multiple app instances; distributed locking is explicitly out of scope. |

The database `CHECK (balance >= 0)` remains a last defense. It is not the main concurrency strategy.

### 6.5 Concurrency test shape

This is an advanced hardening test, not a prerequisite for the first working transfer.

Fund one Sender with `100.00`, create two Recipients, and start two independent transactions simultaneously, each trying to send `80.00`. Assert:

- exactly one request commits and one is rejected for insufficient funds;
- final Sender balance is `20.00`;
- total Recipient credits equal `80.00`;
- exactly one new Transaction exists;
- no balance is negative;
- a separate set of opposite-direction transfers completes without lost updates and follows deterministic lock ordering.

## 7. Authentication and security design

The early learning concepts are password hashing, credential verification, the Spring Security filter chain, a signed expiring JWT, `SecurityContext`, and ownership derived from the authenticated User. These are **MUST IMPLEMENT FOR MVP**.

The exact encoder profile, claim set, key length, and token lifetime below are final-build configuration decisions. Implement them after the basic flow is understood.

### 7.1 Signup and password handling

- Normalize email before lookup and persistence by trimming and lowercasing with locale-independent behavior.
- Use a `DelegatingPasswordEncoder` with encoding ID `pbkdf2@SpringSecurity_v5_8`, mapped to `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()`. The profile uses a 16-byte random salt, 310,000 PBKDF2-HMAC-SHA-256 iterations, and a 256-bit result. Stored values begin with `{pbkdf2@SpringSecurity_v5_8}` so a later parameter profile can be distinguished.
- Accept the PRD range of 8-72 Unicode characters. PBKDF2 consumes the complete UTF-8 input and has no BCrypt-style 72-byte truncation boundary. Tests must include a 72-character multi-byte password and prove that changing its final character changes authentication. Never pre-truncate or silently normalize passwords.
- Log neither registration payloads nor encoded password values.

### 7.2 Sign-in

```text
AuthController
  -> SignInRequest validation
  -> AuthenticationService
       -> normalize email
       -> load User
       -> PasswordEncoder.matches(raw, hash)
       -> generic failure if either lookup or match fails
       -> JwtService.issue(subject = user.id)
  -> TokenResponse(accessToken, "Bearer", expiresAt)
```

The same `401 AUTHENTICATION_FAILED` response is used for an unknown email and a wrong password.

### 7.3 JWT profile

- Signed compact JWT using HS256 because one application both issues and verifies tokens.
- Secret is a Base64-encoded random value of at least 256 bits supplied as `PAYNEST_JWT_SECRET`; startup fails clearly if it is absent or too short outside explicit tests.
- Claims: `sub` = decimal User ID, `iat`, and `exp`. Optional `iss = paynest` may be fixed. Do not include email, password information, roles, Wallet ID, or balance.
- Default lifetime: 60 minutes through `PAYNEST_JWT_TTL`; this is configuration, not a new product feature.
- No refresh token, revocation list, logout endpoint, session store, or server-side JWT persistence.

### 7.4 Protected request flow

```text
Authorization: Bearer <JWT>
    -> JwtAuthenticationFilter (once per request)
    -> parse and verify signature, algorithm, expiry, subject
    -> load current User by subject
    -> place common.security.AuthenticatedUser(User ID) in SecurityContext
    -> controller extracts User ID and passes it to its application service
```

`AuthenticatedUser` is a feature-neutral immutable value in `common.security`; it does not depend on User or Auth packages. Extracting its User ID in a controller is identity plumbing, not a business authorization decision. The ID never comes from a path, query, or body. Application services use the trusted ID to perform ownership-constrained queries. A token whose subject is invalid or no longer resolves to a User is rejected with `401 INVALID_TOKEN`.

### 7.5 Spring Security policy

- Stateless session creation policy.
- CSRF disabled for this bearer-token-only JSON API; adding browser cookie authentication later would require revisiting this decision.
- Permit `POST /api/auth/signup`, `POST /api/auth/signin`, and the OpenAPI/Swagger documentation paths without JWT. Require authentication for all other application requests.
- No roles or authorities are modeled. Authorization is ownership logic in application/repository queries.
- `JwtAuthenticationEntryPoint` renders the shared `ApiError` for authentication failures. An access-denied handler is retained for framework-level `403`, though current ownership behavior normally returns `404` through the application layer.
- Do not configure CORS unless a separately deployed frontend is approved.

## 8. Seven-endpoint API architecture

Exactly these endpoint mappings exist.

| Method and path | Controller | Request DTO | Application service | Repository work | Response DTO |
|---|---|---|---|---|---|
| `POST /api/auth/signup` | `AuthController` | `SignUpRequest` | `RegistrationService.register` | normalize/check/insert User; insert Wallet atomically | `SignUpResponse(UserResponse, WalletResponse)` |
| `POST /api/auth/signin` | `AuthController` | `SignInRequest` | `AuthenticationService.signIn` | find User by normalized email; no write | `TokenResponse` |
| `GET /api/users/me` | `UserController` | none | `UserQueryService.currentUser` | find User by authenticated ID | `UserResponse` |
| `GET /api/wallet` | `WalletController` | none | `WalletQueryService.currentWallet` | find Wallet by authenticated User ID | `WalletResponse` |
| `POST /api/wallet/transfers` | `TransferController` | `TransferRequest` | `TransferService.transfer` | resolve users/wallets; lock Wallets; insert Transaction | `TransferResponse` |
| `GET /api/transactions` | `TransactionController` | validated `page`, `size` params | `TransactionQueryService.history` | participant query plus count; fetch/project needed counterparties | `TransactionPageResponse` |
| `GET /api/transactions/{id}` | `TransactionController` | positive `Long id` path value | `TransactionQueryService.detail` | find by Transaction ID and participant Wallet ID in one ownership-constrained query | `TransactionDetailResponse` |

### 8.1 Request and response flow notes

**Sign up:** bean validation rejects malformed input before the service. The service remains responsible for normalization, duplicate handling, hashing, and atomic persistence. Return `201` only after a successful commit path.

**Sign in:** validation checks presence and email shape. The service performs a constant-style generic credential decision and returns the JWT and expiry from one clock source.

**Current User:** `UserController` extracts the common authenticated principal's User ID and passes it to the query service, which loads and maps a safe profile. No password-related field reaches the mapper.

**Wallet:** `WalletController` passes the trusted principal User ID to `WalletQueryService`, which queries by `user_id`. If the Wallet is missing, return `404 WALLET_NOT_FOUND` and treat the absence as an invariant failure internally.

**Transfer:** the controller passes only recipient email and amount plus the trusted current principal. The response reuses the summary shape with direction fixed to `SENT` and includes Sender `balanceAfter`.

**History:** validate `page >= 0` and `1 <= size <= 100`; use a fixed pageable sort of `createdAt DESC, id DESC`. `TransactionQueryService` resolves the trusted User's Wallet ID through `WalletRepository`, then uses that ID internally in the participant query. The mapper returns `SENT` when the current Wallet ID equals `senderWallet.id`; otherwise it returns `RECEIVED`. It exposes only the other participant as counterparty. A Wallet query helper can be extracted later if this lookup becomes meaningfully reused.

**Detail:** query by Transaction ID with a predicate requiring the current Wallet ID to equal the Sender or Receiver. An empty result covers nonexistent and unrelated records and returns the same `404`. Derive direction and map Sender and Recipient receipt data explicitly.

### 8.2 DTO validation

| DTO/value | Rules |
|---|---|
| `SignUpRequest.name` | required; trim; 2-100 characters |
| `SignUpRequest.email` | required; syntactically valid; max 254; normalize before lookup |
| `SignUpRequest.password` | required; 8-72 characters |
| `SignInRequest.email/password` | both required; email syntactically valid |
| `TransferRequest.recipientEmail` | required; valid; max 254; normalized |
| `TransferRequest.amount` | required; positive; at most 17 integer and 2 fractional digits so it fits `NUMERIC(19,2)` |
| `page` | integer, default 0, minimum 0 |
| `size` | integer, default 20, range 1-100 |
| transaction `id` | positive `Long` |

DTO validation provides friendly errors. Services recheck self-transfer, recipient existence, exact monetary scale, sufficient funds, and ownership because those rules must hold regardless of the caller.

## 9. Error architecture

### 9.1 Stable response

```json
{
  "timestamp": "2026-10-06T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Transfer amount must be greater than zero",
  "path": "/api/wallet/transfers",
  "fieldErrors": {
    "amount": "must be greater than 0.00"
  }
}
```

`fieldErrors` is optional. `GlobalExceptionHandler` handles controller/application exceptions; the security entry point and access-denied handler use the same error writer because filter failures occur before controller advice.

### 9.2 Exception mapping

| Condition | Status | Stable code | Notes |
|---|---:|---|---|
| DTO/path/page validation | 400 | `VALIDATION_ERROR` | Safe field messages only |
| Bad JSON/type mismatch | 400 | `VALIDATION_ERROR` | Do not expose parser internals |
| Invalid credentials | 401 | `AUTHENTICATION_FAILED` | Same response for unknown user/wrong password |
| Missing/invalid/expired/deleted-subject JWT | 401 | `INVALID_TOKEN` | Never echo token content |
| Framework authorization denial | 403 | `ACCESS_DENIED` | Reserved for authenticated but framework-denied requests |
| User/Recipient not found | 404 | `USER_NOT_FOUND` | Safe resource message |
| Wallet not found | 404 | `WALLET_NOT_FOUND` | Log invariant concern safely |
| Unknown/unrelated Transaction | 404 | `TRANSACTION_NOT_FOUND` | Same response for both |
| Duplicate normalized email | 409 | `EMAIL_ALREADY_EXISTS` | Translate authoritative unique constraint |
| Deadlock or lock timeout | 409 | `CONFLICT` | Transaction already rolled back |
| Self-transfer | 422 | `SELF_TRANSFER_NOT_ALLOWED` | Business rule |
| Insufficient funds | 422 | `INSUFFICIENT_BALANCE` | No state committed |
| Unexpected Transfer/database failure | 500 | `TRANSACTION_FAILED` | Generic message; Transfer already rolled back |
| Other unexpected failure | 500 | `INTERNAL_ERROR` | Generic client message; safe server log |

Use a small set of meaningful unchecked business exceptions, not a class per endpoint, error code, or HTTP status. Validation failures should normally come from Jakarta Validation, authentication failures from Spring Security handlers, and persistence conflicts from translated Spring data-access exceptions. The remaining domain cases need only clear exceptions such as insufficient balance and self-transfer; resource-not-found cases may share a small base only if that makes mapping simpler.

`GlobalExceptionHandler`, the JWT authentication entry point, and the access-denied handler are sufficient. Do not introduce correlation-ID infrastructure, an observability platform, or a complex exception hierarchy for this MVP. Map known integrity constraints safely and never return raw database messages. Logs may contain a sanitized stack trace but never credential payloads, JWTs, hashes, SQL details, or secrets.

## 10. PostgreSQL design

### 10.1 Logical schema

```text
users
  id PK
  email UNIQUE
      1
      |
      | wallets.user_id UNIQUE FK
      1
wallets
  id PK
  balance CHECK >= 0
      1                         1
      |                         |
      | sender_wallet_id        | receiver_wallet_id
      N                         N
transactions
```

### 10.2 DDL contract

`users`

- `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
- `name VARCHAR(100) NOT NULL`
- `email VARCHAR(254) NOT NULL`
- `password_hash VARCHAR(255) NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- constraint `uk_users_email UNIQUE (email)`

`wallets`

- `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
- `user_id BIGINT NOT NULL`
- `balance NUMERIC(19,2) NOT NULL DEFAULT 0.00`
- `currency CHAR(3) NOT NULL DEFAULT 'USD'`
- `created_at TIMESTAMPTZ NOT NULL`
- constraint `uk_wallets_user UNIQUE (user_id)`
- constraint `fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id)`
- constraint `ck_wallets_non_negative_balance CHECK (balance >= 0.00)`
- constraint `ck_wallets_usd_currency CHECK (currency = 'USD')`

`transactions`

- `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`
- `sender_wallet_id BIGINT NOT NULL`
- `receiver_wallet_id BIGINT NOT NULL`
- `amount NUMERIC(19,2) NOT NULL`
- `status VARCHAR(20) NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- constraint `fk_transactions_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets(id)`
- constraint `fk_transactions_receiver_wallet FOREIGN KEY (receiver_wallet_id) REFERENCES wallets(id)`
- constraint `ck_transactions_positive_amount CHECK (amount > 0.00)`
- constraint `ck_transactions_distinct_wallets CHECK (sender_wallet_id <> receiver_wallet_id)`
- constraint `ck_transactions_completed_status CHECK (status = 'COMPLETED')`

Named constraints make safe exception classification and tests clearer.

### 10.3 Indexes

- `uk_users_email` supports sign-in and recipient lookup.
- `uk_wallets_user` supports current-Wallet lookup and enforces one-to-one.
- `idx_transactions_sender_history (sender_wallet_id, created_at DESC, id DESC)` supports the sender half of participant history.
- `idx_transactions_receiver_history (receiver_wallet_id, created_at DESC, id DESC)` supports the receiver half.

The history query filters `sender_wallet_id = :walletId OR receiver_wallet_id = :walletId`. PostgreSQL can combine the two indexes, apply the fixed sort, and return the bounded page. Verify the query plan after representative fixture volume; do not add speculative indexes before evidence.

### 10.4 Money and time

- Java requests and entities use `BigDecimal`; responses preserve two decimal places.
- Construct monetary constants from decimal strings, never binary floating point.
- Reject scale greater than 2. Do not silently round a transfer amount.
- Set JDBC/Hibernate and JSON behavior to preserve decimal values.
- Use `Instant` in Java and `TIMESTAMPTZ` in PostgreSQL; configure the application and database to use UTC.
- Use an injectable `Clock` for creation and token timestamps so tests can be deterministic.

## 11. Migration and configuration design

### 11.1 Flyway

- Configure Flyway and verify database connectivity in the foundation phase without front-loading domain tables.
- Flyway and Hibernate target the same configured PostgreSQL schema (the default `public` schema unless explicitly externalized); the foundation does not create or consume a versioned domain migration.
- `V1__create_users.sql` is introduced with the User mapping and contains the `users` table and named constraints.
- `V2__create_wallets.sql` is introduced with the Wallet mapping and contains the `wallets` table, User foreign key, unique constraint, and Wallet checks.
- `V3__create_transactions.sql` is introduced with the Transaction mapping and contains the `transactions` table, foreign keys, checks, and both history indexes.
- Later schema changes append new versioned files; do not edit an already-shared migration.
- Run Flyway at application startup in local and test profiles.
- Set Hibernate schema mode to `validate`; schema generation/update is not the normal workflow.
- Keep product fixture data out of versioned production migrations.

### 11.2 Configuration

`application.yml` contains safe defaults and placeholders. Supply database credentials, JWT signing material, and deploy-specific values through the listed environment variables.

| Setting | Suggested environment variable |
|---|---|
| JDBC URL | `PAYNEST_DB_URL` |
| DB user | `PAYNEST_DB_USERNAME` |
| DB password | `PAYNEST_DB_PASSWORD` |
| Test JDBC URL | `PAYNEST_TEST_DB_URL` |
| Test DB user | `PAYNEST_TEST_DB_USERNAME` |
| Test DB password | `PAYNEST_TEST_DB_PASSWORD` |
| JWT secret | `PAYNEST_JWT_SECRET` |
| JWT lifetime | `PAYNEST_JWT_TTL` |
| PostgreSQL row-lock wait | `PAYNEST_DB_LOCK_TIMEOUT` (default local/test `5s`) |

Do not commit real `.env` files. A `.env.example` may document variable names using obvious non-secret placeholders. Avoid configuration systems, Vault, or cloud secret managers in the MVP.

During advanced concurrency hardening, run `SET lock_timeout` for each datasource connection using `PAYNEST_DB_LOCK_TIMEOUT`. Add a PostgreSQL integration test in which a second connection holds a Wallet row lock; verify a bounded wait, rollback, and `409 CONFLICT`. This configuration is not a prerequisite for the first CRUD/transfer learning flow, but it is part of final hardening. Do not add an automatic retry.

### 11.3 Development/test funding

There is no funding migration and no funding controller. For a local demonstration, a developer may run `scripts/dev/fund-wallet.sql` manually against known local test users. Automated tests should use `@Sql` fixtures, repository setup helpers in test source, or direct test JDBC setup.

Any helper must be unavailable from the production application classpath/profile. This mechanism exists only to make Send Money demonstrable and testable.

## 12. Testing architecture

Testing grows with the implementation. A dedicated developer-managed `paynest_test` database is the required PostgreSQL integration approach, but advanced concurrency machinery should not delay the first business flow.

### 12.1 Essential early tests

Add these alongside the corresponding feature:

- successful registration and automatic zero-USD Wallet creation;
- normalized duplicate email rejection and password hashing;
- successful login and generic invalid-credential handling;
- one JWT-protected endpoint rejecting missing/invalid authentication;
- current User and Wallet ownership;
- successful basic transfer with exact balances;
- invalid amount, insufficient balance, unknown Recipient, and self-transfer;
- one completed Transaction for success and none for a rejected Transfer;
- sent/received history, pagination basics, participant detail, and unrelated detail `404`;
- registration and Transfer rollback at the business transaction boundary.

Use fast JUnit tests for pure Wallet arithmetic, normalization, direction mapping, and small business decisions. Use `@SpringBootTest` with MockMvc and the dedicated PostgreSQL test database for HTTP, Spring Security, JPA mappings, Flyway, transactions, and repository queries. Do not use H2 where PostgreSQL behavior matters.

### 12.2 Advanced hardening tests

Add these after the basic application flow works and before final acceptance:

- concurrent duplicate registration;
- competing transfers that would overspend one Wallet;
- opposite-direction transfers and deterministic lock ordering;
- pessimistic lock acquisition and bounded lock-timeout behavior;
- forced failure after a debit but before Transaction insertion;
- exact PostgreSQL unique, foreign-key, and check-constraint behavior;
- a 20-item history query-count or equivalent SQL-shape check that prevents N+1 counterparty loading;
- PBKDF2 multi-byte boundary behavior and invalid/tampered/expired/deleted-subject JWT cases;
- exact PRD error codes/schema and all seven OpenAPI product paths.

Concurrency tests must run calls in separate threads, transactions, and database connections. Coordinate starts with a latch/barrier, apply test timeouts, and assert committed state after both operations finish. Do not wrap the test itself in one transaction.

### 12.3 Test data and cleanup

Use `@Sql`, test-only repository/JDBC fixtures, or isolated setup helpers to fund Wallets. Prefer explicit cleanup between integration cases inside the dedicated test database. Before any cleanup or schema reset, execute `select current_database()` and require exactly `paynest_test`; abort without mutation otherwise. Generated ID values are not part of assertions. None of this creates a production funding path.

## 13. OpenAPI design

Use springdoc-openapi to generate one API description from the seven controllers and DTOs.

- Define a bearer security scheme named `bearerAuth`, type HTTP, scheme `bearer`, format `JWT`.
- Mark sign-up and sign-in public; apply `bearerAuth` to the other five operations.
- Describe field length, email, decimal precision/scale, page bounds, and ID constraints from DTO validation.
- Document the exact success DTOs/statuses and representative `ApiError` responses from the PRD.
- Include examples with invented identities and placeholder tokens only.
- Group operations as Authentication, User, Wallet, and Transactions. The Transfer endpoint remains under Wallet to match the public API even though its implementation has a focused transfer slice.
- Add an automated integration assertion that the generated document contains exactly the seven MVP paths; Swagger UI resources are documentation assets, not extra product APIs.

## 14. Direct local delivery

The completed application runs directly as one Spring Boot process against a developer-managed PostgreSQL server:

```text
Application -> Tests -> Concurrency hardening -> OpenAPI -> Direct local verification
```

Document PostgreSQL 18.6, Java 25, the Maven Wrapper, creation of separate `paynest` and `paynest_test` databases, required environment variables, direct startup, test execution, fixture funding, OpenAPI access, and orderly shutdown. Flyway must migrate the clean local database at startup and Hibernate must validate the resulting schema. Docker, Compose, Testcontainers, images, and other container-based tooling are outside the project.

No public Wallet-funding service or route is added for demonstrations; use the documented local SQL fixture.

## 15. Architecture decisions

### ADR-1 - Package-by-feature modular monolith

- **Decision:** One Spring Boot application organized by business feature, with in-process calls and one database.
- **Why:** Matches the PRD and keeps transactional and learning flows visible.
- **Alternatives:** Package-by-layer; microservices.
- **Rejected because:** Package-by-layer weakens feature ownership; microservices add networking, distributed consistency, and infrastructure outside the MVP.

### ADR-2 - PostgreSQL with JPA/Hibernate

- **Decision:** PostgreSQL is the single database; Spring Data JPA/Hibernate maps the three entities.
- **Why:** Supports the required relationships, constraints, ACID transactions, and row locking while teaching the requested stack.
- **Alternatives:** H2 as primary/test substitute; JDBC-only persistence; multiple databases.
- **Rejected because:** H2 does not prove PostgreSQL behavior; JDBC-only loses central JPA learning objectives; multiple databases contradict local atomicity.

### ADR-3 - Wallet owns User one-to-one; Transaction owns Wallet references

- **Decision:** `wallets.user_id` is the unique owning one-to-one FK. Transaction has two unidirectional many-to-one Wallet references. Wallet has no Transaction collections.
- **Why:** The schema naturally owns these FKs and repository pagination is safer than loading unbounded collections.
- **Alternatives:** User-owned Wallet FK; bidirectional Transaction collections; join tables.
- **Rejected because:** They make the schema/query model less direct or create unnecessary large graphs.

### ADR-4 - BigDecimal and NUMERIC(19,2)

- **Decision:** Exact two-decimal monetary representation with rejected excess precision.
- **Why:** Prevents floating-point errors and matches the PRD.
- **Alternatives:** `double`; integer cents.
- **Rejected because:** `double` is unsafe for money; integer cents is valid but bypasses the intended BigDecimal/JPA learning objective.

### ADR-5 - Stateless HS256 JWT

- **Decision:** JJWT-signed, expiring HS256 access tokens with User ID subject; no server session or refresh token.
- **Why:** One application issues and verifies tokens, making a symmetric secret simple and sufficient for the MVP.
- **Alternatives:** Sessions, asymmetric keys, OAuth2 authorization server.
- **Rejected because:** Sessions conflict with the JWT requirement; asymmetric/OAuth2 infrastructure adds no MVP behavior.

### ADR-6 - Business-operation transaction boundaries

- **Decision:** Registration and transfer each have one application-service transaction boundary.
- **Why:** Each boundary matches one business invariant and keeps partial repository writes from committing.
- **Alternatives:** Controller transactions; repository-method transactions; entity callbacks.
- **Rejected because:** They couple transport or persistence details to a cross-step business guarantee and risk partial commits.

### ADR-7 - Pessimistic Wallet row locks

- **Decision:** Lock both Wallet rows for update in ascending ID order within the transfer transaction.
- **Why:** It is the simplest correct strategy for state-dependent two-row mutation and directly prevents overspending/lost updates.
- **Alternatives:** Optimistic version/retry, conditional SQL, serializable isolation, distributed locks.
- **Rejected because:** They add retries, obscure the domain flow, broaden conflicts, or violate scope.

### ADR-8 - Flyway SQL migrations plus Hibernate validation

- **Decision:** Flyway owns schema evolution; each entity story introduces its table through the next append-only migration, and Hibernate only validates.
- **Why:** Produces explicit, reviewable PostgreSQL constraints and repeatable environments without front-loading tables before the story that uses them.
- **Alternatives:** One upfront migration for the complete schema; `ddl-auto=update`; Liquibase.
- **Rejected because:** The upfront schema weakens story independence; automatic update is not a reliable shared workflow; Liquibase is capable but adds no advantage for this small SQL-oriented schema.

### ADR-9 - Dedicated developer-managed PostgreSQL integration database

- **Decision:** Run persistence, rollback, and concurrency integration tests against a dedicated developer-managed PostgreSQL database named exactly `paynest_test`, configured by `PAYNEST_TEST_DB_*` environment variables.
- **Why:** The most important risks are engine-specific constraints and lock semantics, while the approved project direction excludes container tooling. An exact database-name guard prevents destructive setup from targeting normal development data.
- **Alternatives:** H2; mocks only; container-provisioned PostgreSQL; reuse of the normal `paynest` development database.
- **Rejected because:** H2 and mocks cannot prove PostgreSQL behavior; container provisioning is outside scope; sharing the development database creates unacceptable cleanup risk.

### ADR-10 - Explicit DTOs and manual mapping

- **Decision:** Java records/classes define API DTOs and small explicit mappers convert query/entity data.
- **Why:** Prevents entity leakage/recursion and keeps the mapping understandable.
- **Alternatives:** Serialize entities; introduce a mapping framework.
- **Rejected because:** Entity serialization is unsafe; a mapping dependency offers little value for seven endpoints and three entities.

## 16. Future microservice learning path - not MVP architecture

**MVP:**

```text
One Spring Boot modular monolith
              |
              v
      One PostgreSQL database
```

**Future learning exercise only:**

```text
Auth Service | User Service | Wallet Service | Transaction Service
                              |
                              v
               messaging and distributed consistency
```

The current package boundaries suggest possible future Auth, User, Wallet, and Transaction services. Extraction would require each service to own its data and API. The current direct calls would become network contracts, authentication trust would need a cross-service design, and Transaction history would need data replicated or queried across boundaries.

Most importantly, the current transfer guarantee depends on one local database transaction. Splitting Wallet and Transaction ownership would remove that guarantee and require explicit distributed-consistency decisions: idempotency, durable messaging/outbox, sagas/compensation, retry policy, and reconciliation. API Gateway, service discovery, Kafka, separate databases, and observability would then be evaluated against concrete requirements.

None of those mechanisms belongs in the MVP. Do not add domain events, an outbox, Kafka topics, service-to-service REST, separate databases, sagas, distributed transactions, an API Gateway, or service discovery merely to make extraction easier. Future extractability comes from readable feature ownership and one focused Transfer use case, not premature distributed architecture. The monolith should not duplicate data or weaken foreign keys to resemble a future system.

## 17. Learner implementation roadmap

Features should be built in learning order, not document order. Tests are written alongside every phase; the advanced stages harden an already understandable flow rather than front-loading concurrency machinery. Transaction persistence is introduced before the internal Transfer flow that uses it. The public Transfer endpoint is exposed only after rollback behavior, the concurrency problem, and ordered pessimistic locking are understood and implemented.

| Phase | Implement | Learn | Gate before moving on |
|---:|---|---|---|
| 1. Project foundation | Maven wrapper, Java/Boot baseline, package skeleton, basic config | application startup, dependency management, profiles | context starts and a smoke test passes |
| 2. PostgreSQL + Flyway | local/test datasources, Flyway wiring, Hibernate `validate`; no domain tables yet | SQL ownership and migration lifecycle | clean development and dedicated test databases connect without application-generated schema |
| 3. User entity | `V1` users migration, User mapping/repository, normalization, timestamps | entity lifecycle, identity IDs, uniqueness | migration, persist/load, and normalized-email tests pass |
| 4. Wallet entity + 1:1 | `V2` wallets migration, unidirectional Wallet-to-User mapping and balance methods | `@OneToOne`, owning side, BigDecimal | migration, unique Wallet, and non-negative balance rules pass |
| 5. Registration + atomic Wallet | signup DTO/controller, PBKDF2, User+Wallet transaction | validation and first `@Transactional` business boundary | success, duplicate, and Wallet-failure rollback pass |
| 6. Spring Security | filter chain and public/protected policy | authentication versus authorization | Auth remains public; protected route returns `401` |
| 7. JWT | issue/validate token, principal, SecurityContext | stateless bearer authentication | valid login/token and basic invalid-token tests pass |
| 8. User and Wallet APIs | `/users/me`, `/wallet`, DTO mapping | ownership-derived queries | each endpoint returns only current-User data |
| 9. Transaction entity | `V3` transactions migration and indexes, sender/receiver Wallet mappings, immutable completed record | `@ManyToOne`, FK ownership, immutable records | staged migration succeeds and persistence constraints pass |
| 10. Basic Send Money internals | `TransferService`, amount/self/recipient/balance rules, completed insert; no public endpoint yet | orchestration and exact balance mutation | service-level non-concurrent happy path and business failures pass |
| 11. Transaction and rollback deepening | verify Transfer transaction boundary and forced mid-operation failure | ACID, propagation, rollback semantics | debit/credit/record always commit or roll back together |
| 12. Concurrency problem | reproduce or trace a competing-overspend race | lost updates and race conditions | a concurrent test or documented trace demonstrates the failure mode |
| 13. Pessimistic locking and Transfer API | ordered `PESSIMISTIC_WRITE` query, `TransferController`, PRD response | row locks, isolation, deterministic ordering | endpoint success creates one record and competing operations cannot overspend |
| 14. Concurrency hardening tests | overspend, opposite direction, lock timeout, PostgreSQL constraints | real multi-transaction testing and deadlock reduction | advanced dedicated-PostgreSQL suite passes repeatedly |
| 15. History and detail | participant queries, direction, pagination, ownership check | joins, DTO projection, secure object access | sent/received, page metadata, and unrelated `404` pass |
| 16. Exception/error hardening | `GlobalExceptionHandler`, security writers, exact PRD codes | stable safe REST failures | contract tests pass and no internals leak |
| 17. OpenAPI | seven operations, schemas, bearer scheme, errors | API contract documentation | generated spec contains exactly seven product paths |
| 18. Direct local verification | prerequisites, environment, development/test databases, startup and smoke journey | external configuration and repeatable local operations | clean direct startup migrates, validates, and serves the monolith |

## 18. MVP Definition of Done and consistency check

| Constraint | Architecture result |
|---|---|
| One Spring Boot application | Yes - one `PayNestApplication` and deployable jar |
| One PostgreSQL database | Yes - one datasource/schema/database |
| Exactly three persisted domain entities | Yes - User, Wallet, Transaction |
| Exactly seven MVP endpoints | Yes - mapped in Section 8; no additions |
| User to Wallet 1:1 | Yes - unique `wallets.user_id` and JPA one-to-one |
| Wallet to Transaction 1:N conceptually | Yes - two Transaction many-to-one FKs, no Wallet collections |
| Transfer is not an entity | Yes - application-only `transfer` slice |
| Atomic registration | Yes - one `RegistrationService` transaction |
| Atomic transfer | Yes - one `TransferService` transaction |
| Concurrent transfers cannot overspend | Yes - ordered pessimistic row locks plus DB check |
| Ownership-protected history/detail | Yes - current-Wallet participant queries; unrelated detail is 404 |
| Failed transfer creates no Transaction | Yes - insert shares rollback boundary; only COMPLETED persists |
| Exact money | Yes - `BigDecimal` and `NUMERIC(19,2)` |
| JWT authentication | Yes - stateless Spring Security/JJWT bearer flow |
| No public funding endpoint | Yes - local/test fixtures only |
| No microservice infrastructure | Yes - no gateway, broker, discovery, cache, distributed transaction, or Kubernetes |
| Future microservices remain future scope | Yes - documented only in Section 16 |

## 19. Assumptions and conflict report

Architecture-owned configuration assumptions:

- JWT access-token lifetime starts at 60 minutes and is externalized.
- PBKDF2 is pinned to the stored `pbkdf2@SpringSecurity_v5_8` profile. Future tuning must introduce a distinct encoder ID so existing hashes remain verifiable.
- HS256 is sufficient while one application exclusively issues and verifies tokens.
- A `Clock` bean provides deterministic time for entities and tokens.
- A manual local SQL funding helper is acceptable under the PRD's non-production fixture allowance.

No proposed architecture decision conflicts with the finalized PRD. If a later implementation requires another endpoint, persisted entity, database, messaging system, role model, or public funding path, it is a product-scope change and must return to PRD review rather than being introduced as an architecture convenience.

## 20. Refinement Summary

### What was preserved

- One package-by-feature Spring Boot modular monolith and one PostgreSQL database.
- Exactly three persisted entities and seven MVP product endpoints.
- User-Wallet one-to-one and Transaction-to-Wallet many-to-one mappings without large bidirectional collections.
- Exact money, strong PostgreSQL constraints, Flyway, Hibernate validation, JWT security, DTO boundaries, atomic registration, atomic Transfer, pessimistic locking, ownership checks, OpenAPI, dedicated PostgreSQL testing, and direct local delivery.

### What was simplified

- `TransferService` remains the required transfer coordinator, but it can initially use the three owning repositories directly.
- `WalletTransferAccess`, `TransactionRecordingService`, `WalletProvisioningService`, and similar helpers are optional refactorings rather than mandatory classes.
- Concrete services are preferred; interfaces require a real alternative implementation or external boundary.
- Error handling uses one global handler, Spring Security handlers, and a small meaningful exception set without correlation-ID or observability infrastructure.
- The package tree is a practical starting point rather than a compulsory one-class-per-responsibility inventory.

### What moved to advanced learning

- Pessimistic locking implementation follows the basic transactional transfer flow.
- Deterministic lock ordering, lock timeout/SQLSTATE mapping, concurrent overspending tests, forced mid-transfer failure, exact PostgreSQL constraint tests, and N+1/query-count verification are advanced hardening steps completed before final acceptance.
- OpenAPI and direct local verification remain later phases after application behavior, tests, and concurrency hardening.

### Remaining assumptions

- JWT lifetime begins at 60 minutes and remains externalized.
- The stored PBKDF2 profile, HS256 key requirements, injectable Clock, and local-only SQL funding helper remain as documented in Section 19.
- Exact helper-service extraction is an implementation refactoring decision; the responsibilities and transaction boundaries in this document remain binding.

### PRD conflicts

No conflict was discovered. The finalized PayNest PRD remains the source of truth; this refinement changes implementation sequencing and optional class boundaries, not product behavior or scope.
