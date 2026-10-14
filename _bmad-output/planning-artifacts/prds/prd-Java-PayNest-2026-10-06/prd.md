---
title: PayNest Product Requirements Document
status: final
created: 2026-10-06
updated: 2026-10-12
---

# Product Requirements Document: PayNest

## 1. Executive Summary

PayNest is a small fintech-style digital wallet backend built as a Java and Spring Boot learning project. It lets a person register, sign in securely, view an automatically created USD wallet, transfer money to another registered PayNest user, and view sent and received transactions. The MVP is an API-only backend; a web or mobile user interface is not included.

The product is intentionally a modular monolith: one Spring Boot application, one deployment unit, and one PostgreSQL database, organized into clear Authentication, User, Wallet, Transaction, and Common modules. The design prioritizes learning value, simplicity, correctness, maintainability, and realistic backend practices—in that order. It must demonstrate secure authentication, precise money handling, atomic database operations, concurrency safety, validation, consistent errors, tests, and API documentation without adding enterprise infrastructure or unrelated fintech features.

This PRD is the source of truth for MVP scope, observable behavior, business rules, API contracts, acceptance criteria, and quality expectations. It feeds architecture, epic and story creation, implementation, and testing without turning PayNest into a production banking platform. Detailed implementation guidance that supports—but does not expand—the requirements remains in [addendum.md](addendum.md).

## 2. Product Vision

PayNest should feel like a small but coherent backend product rather than a collection of disconnected tutorials. A developer should be able to trace a complete business journey from an HTTP request through authentication, validation, application logic, persistence, transaction management, and a documented response.

The defining learning moment is a correct money transfer: two Wallet balances and one Transaction must change together, or none of them may change. The monolith should be easy to understand today and organized well enough that future service boundaries are visible, while making no attempt to implement distributed systems in the MVP.

PayNest is primarily a backend learning project with realistic business behavior. Simplicity and understanding take priority over production-scale complexity. Every architectural component must justify its presence through an MVP requirement or a clearly defined learning objective. Do not introduce abstractions, infrastructure, services, patterns, or dependencies solely because they might be useful in a future microservice architecture.

## 3. Goals

- **G-1 — Complete wallet journey:** Support registration, authentication, Wallet access, money transfer, Transaction history, and Transaction detail through a small REST API.
- **G-2 — Demonstrate backend fundamentals:** Exercise Java, Spring Boot, PostgreSQL, JPA/Hibernate, relationships, Spring Security, JWT, validation, exceptions, transactions, testing, and OpenAPI.
- **G-3 — Preserve financial consistency:** Prevent negative balances, partial transfers, lost updates, duplicate Users, and unauthorized data access.
- **G-4 — Produce maintainable learning code:** Use clear feature boundaries, business logic owned by focused application or domain services, focused dependencies, and tests centered on business behavior.
- **G-5 — Enable later evolution:** Make Authentication, User, Wallet, and Transaction responsibilities recognizable candidates for future extraction without introducing microservice mechanisms now.

### 3.1 Success Measures

- **SM-1 — Journey completeness:** Every in-scope User Journey can be executed against the documented REST API and is covered by passing integration tests. Validates FR-1 through FR-12; pagination boundaries and OpenAPI publication are verified separately by FR-13, FR-14, and their acceptance criteria.
- **SM-2 — Consistency under failure:** Integration tests prove that a failed registration leaves neither a User nor a Wallet. They also prove that a failed Transfer does not change either balance or create a completed Transaction. Validates FR-2 and FR-8.
- **SM-3 — Concurrency safety:** A test with competing transfers from the same Wallet proves that committed debits never exceed the available balance and the Wallet never becomes negative. Validates FR-9.
- **SM-4 — Security boundary:** Tests prove that anonymous or invalid-token requests are rejected and a User cannot retrieve a Transaction unrelated to their Wallet. Validates FR-4 and FR-12.
- **SM-5 — Learning readiness:** OpenAPI documents all seven endpoints, their schemas, JWT usage, and expected errors, and the code remains one modular Spring Boot application. Validates FR-14 and G-2.
- **Counter-metric SM-C1 — Feature count:** More endpoints or fintech capabilities do not indicate success; scope growth that does not support an in-scope journey is a regression.
- **Counter-metric SM-C2 — Coverage percentage:** No arbitrary line-coverage target may replace behavior-focused tests for security, transactions, and business rules.

### 3.2 Learning Outcomes

| Feature or concern | Concepts demonstrated |
|---|---|
| Sign up | REST, DTO validation, JPA persistence, password hashing, uniqueness, one-to-one relationship, transaction management. |
| Sign in | Spring Security, credential authentication, JWT generation, bearer-token flow. |
| Current User | Security context, ownership-derived access, DTO mapping. |
| Wallet | JPA relationships, `BigDecimal`, precision, database constraints, `@OneToOne`, owning-side decisions, and cascade behavior. |
| Transfer | Transaction boundaries around business operations, `@Transactional` as a Spring learning concept, ACID, PostgreSQL concurrency, deterministic lock ordering, validation, rollback, and database consistency. |
| Transaction history | `@ManyToOne` and conceptual `@OneToMany` relationships, repository queries, lazy loading, pagination, sorting, DTO mapping, recursive-serialization avoidance, and object-level authorization. |
| Error handling | Global exception mapping, stable REST error contracts, safe information disclosure. |
| Testing | Unit versus integration tests, security tests, rollback tests, concurrent transaction tests, realistic PostgreSQL behavior. |
| Modular monolith | Package-by-feature, module ownership, service and repository boundaries, in-process collaboration. |
| API documentation | OpenAPI schemas, examples, error responses, JWT security scheme. |
| Direct local operation | Reproducible Spring Boot/PostgreSQL startup, dedicated test-database safety, and externalized configuration. |
| Future microservices | Service-boundary reasoning, API Gateway, REST, Kafka, events, separate databases—documented but not implemented. |

## 4. Non-Goals

The MVP is not a real bank, regulated financial service, payment processor, or production launch. It will not include:

- Bank accounts, payment gateways, cards, real-world banking integrations, deposits, or withdrawals.
- KYC, OTP, email verification, email-based password reset, fraud detection, or compliance certification.
- Loans, currency exchange, multi-currency Wallets, international transfers, refunds, rewards, or cryptocurrency.
- Scheduled or recurring payments.
- Admin dashboards, notifications, complex reports, or operational case-management tools.
- A web or mobile UI.
- Microservices, an API gateway, service discovery, Kafka, separate service databases, distributed transactions, distributed tracing, Kubernetes, or complex DevOps infrastructure.
- Redis, event-driven architecture, or infrastructure introduced only to prepare for future service extraction.
- Docker, Docker Compose, Testcontainers, and other container-based delivery or testing tooling.
- Production-grade availability, throughput, support, or regulatory SLAs.

Future possibilities are described only in Section 22 and are not MVP commitments.

## 5. Target User

### 5.1 Primary learning user

A single intermediate Java developer is building PayNest to practice an end-to-end Spring Boot backend with realistic domain rules. Their job to be done is: “Help me learn how a maintainable backend protects data, enforces business rules, and preserves consistency across a complete API journey.”

### 5.2 Functional product actor

A registered PayNest User uses an API client or a future frontend to authenticate, inspect their Wallet, send money to another User, and review Transactions. All MVP Users have the same permissions; there is no admin or role hierarchy.

### 5.3 Non-users in the MVP

- Unregistered recipients and external bank customers.
- Administrators, compliance analysts, support agents, and merchants.
- Developers seeking a production banking reference architecture.

## 6. Glossary

- **User** — A registered PayNest account identified by a unique normalized email address. A User owns exactly one Wallet.
- **Wallet** — The single USD balance account automatically created for a User. A Wallet cannot be manually created, deleted, or overdrawn.
- **Transaction** — The immutable record of a successfully committed transfer from one Wallet to another Wallet.
- **Transfer** — The atomic business operation that debits one Wallet, credits another Wallet, and creates one Transaction.
- **Sender** — The authenticated User initiating a Transfer and the owner of the debited Wallet.
- **Recipient** — The registered User identified by `recipientEmail` whose Wallet is credited.
- **Transaction direction** — `SENT` or `RECEIVED`, calculated relative to the authenticated User; it is not persisted as global Transaction state.
- **Transaction status** — The persisted outcome of a Transaction. MVP records use only `COMPLETED` because failed Transfers roll back and do not create a durable Transaction.
- **JWT** — A signed, time-limited JSON Web Token presented as an HTTP bearer token for protected requests.
- **Normalized email** — An email address trimmed of surrounding whitespace and converted to lowercase for lookup and uniqueness.

## 7. User Journeys

- **UJ-1 — Amina creates her PayNest account.** Amina submits her name, email, and password from an API client. After validation, PayNest creates her User and exactly one USD Wallet with a zero balance as one atomic operation. She receives a safe profile and Wallet summary, never a password or hash. If the email already exists or Wallet creation fails, she receives a consistent error and no partial account remains.

- **UJ-2 — Amina signs in and checks her Wallet.** Amina submits valid credentials, receives a time-limited bearer JWT, and uses it to request her profile and Wallet. The API derives her identity from the JWT and returns only her own data. A missing, malformed, invalid, or expired token is rejected.

- **UJ-3 — Amina sends money to Rahim.** [ASSUMPTION A-4: In a test or local demonstration, Amina's Wallet has been funded through non-public fixture data because public funding is out of scope.] She submits Rahim's email and a positive USD amount. PayNest verifies both Users and the balance, locks the affected Wallet rows safely, debits Amina, credits Rahim, records one completed Transaction, and commits all three changes together. Both balances remain unchanged if any step fails.

- **UJ-4 — Rahim reviews a received Transaction.** Rahim requests his paginated history and sees the transfer marked `RECEIVED`; Amina sees the same Transaction marked `SENT`. Either participant can retrieve its detail. An unrelated User receives `404 Not Found` rather than learning whether that Transaction exists.

## 8. Functional Requirements

### 8.1 Registration and automatic Wallet creation

#### FR-1: Register a User

An unauthenticated client can register a User with a name, email, and password. Realizes UJ-1.

**Testable consequences:**

- Valid input returns `201 Created` with the new User profile and Wallet summary.
- Email uniqueness is enforced using the Normalized email, including concurrent requests.
- The password is accepted only for credential processing and is never returned, logged, or stored in plaintext.
- Duplicate email returns `409 Conflict` without revealing credential details.

#### FR-2: Create exactly one Wallet atomically

PayNest automatically creates exactly one Wallet for every successfully registered User in the same database transaction. Realizes UJ-1.

**Testable consequences:**

- The Wallet starts with `balance = 0.00` and `currency = USD`.
- A User cannot create or delete a Wallet through any API.
- If User or Wallet persistence fails, neither record remains committed.
- Database constraints prevent more than one Wallet from referencing the same User.

**Feature acceptance:** AC-AUTH-1 through AC-AUTH-3 and AC-WALLET-1.

### 8.2 Authentication and current User

#### FR-3: Sign in

An unauthenticated client can exchange a valid email and password for a JWT. Realizes UJ-2.

**Testable consequences:**

- Valid credentials return `200 OK` with a bearer token and expiry metadata.
- Invalid credentials return `401 Unauthorized` with the same generic response whether the email is unknown or the password is wrong.
- No refresh-token, logout, or token-revocation API is introduced in the MVP.

#### FR-4: Authenticate protected requests

PayNest requires a valid JWT for User, Wallet, Transfer, and Transaction endpoints. Realizes UJ-2 through UJ-4.

**Testable consequences:**

- A protected request without a bearer token returns `401 Unauthorized`.
- A malformed, invalidly signed, or expired JWT returns `401 Unauthorized`.
- The server derives the acting User from the verified JWT rather than from a caller-supplied User or Wallet identifier.

#### FR-5: View the current User profile

An authenticated User can retrieve their own safe profile. Realizes UJ-2.

**Testable consequences:**

- `GET /api/users/me` returns the authenticated User's public ID, name, email, and creation timestamp.
- The response never includes a password, password hash, security configuration, or another User's private data.

**Feature acceptance:** AC-AUTH-4 through AC-AUTH-7.

### 8.3 Wallet access and Transfers

#### FR-6: View Wallet balance

An authenticated User can retrieve their own Wallet. Realizes UJ-2.

**Testable consequences:**

- The response contains the currency, exact two-decimal balance, and Wallet creation timestamp.
- The Wallet is selected from the authenticated identity; the endpoint accepts no User ID or Wallet ID.
- A missing Wallet is treated as an internal data-integrity error and returned safely as `404 Not Found` without technical internals.

#### FR-7: Validate a Transfer

An authenticated Sender can request a Transfer to a registered Recipient by Normalized email. [ASSUMPTION A-1: Recipient email is the Transfer identifier so clients do not need an internal Wallet ID.] Realizes UJ-3.

**Testable consequences:**

- `recipientEmail` is required and must identify an existing User.
- `amount` is required, must be greater than `0.00`, must use at most two decimal places, and must fit the supported database precision.
- Sender and Recipient must be different Users.
- Sender must have sufficient balance at the time the Transfer is committed.
- Validation or business-rule failure changes no balance and creates no Transaction.

#### FR-8: Execute a Transfer atomically

PayNest debits the Sender Wallet, credits the Recipient Wallet, and creates one Transaction as a single atomic database operation. Realizes UJ-3.

**Testable consequences:**

- A successful request returns `201 Created`; the Sender's balance decreases once by the exact amount, and the Recipient's balance increases once by the exact amount.
- Any failure before commit rolls back both balance changes and the Transaction insert.
- A Wallet balance is never negative after commit.
- No external or distributed transaction mechanism is used.

#### FR-9: Prevent concurrent overspending and lost updates

PayNest uses a database-level concurrency strategy when changing Wallet balances. Realizes UJ-3.

**Testable consequences:**

- Two simultaneous Transfers cannot both spend the same funds when their combined amount exceeds the Sender's balance.
- Updates to either Wallet are not silently overwritten by a concurrent Transfer.
- Both affected Wallets are locked in a deterministic order to reduce deadlock risk.
- A database check constraint provides a final guard against a negative balance.

**Feature acceptance:** AC-WALLET-2 through AC-WALLET-4 and AC-TRANSFER-1 through AC-TRANSFER-6.

### 8.4 Transaction records and history

#### FR-10: Record a completed Transaction

Each successful Transfer creates exactly one immutable Transaction with sender Wallet, receiver Wallet, amount, status, and creation timestamp. Realizes UJ-3.

**Testable consequences:**

- The persisted status is `COMPLETED`.
- The Transaction amount and involved Wallet references do not change after creation.
- A failed Transfer does not leave a `COMPLETED` Transaction.
- The MVP does not persist `FAILED` records because the Transfer transaction rolls back; failure is represented by the REST error and safe application logs.

#### FR-11: View paginated Transaction history

An authenticated User can retrieve Transactions in which their Wallet is the Sender or Recipient. Realizes UJ-4.

**Testable consequences:**

- Results are sorted newest first and support simple page and size parameters.
- Each item includes ID, relative direction, counterparty identity, amount, currency, status, and creation timestamp. [ASSUMPTION A-5: Counterparty name and email are appropriate receipt data for the two participating Users.]
- The same Transaction appears as `SENT` to the Sender and `RECEIVED` to the Recipient.
- No Transaction unrelated to the authenticated User is returned.

#### FR-12: View Transaction detail securely

An authenticated User can retrieve a Transaction only when their Wallet is its Sender or Recipient. Realizes UJ-4.

**Testable consequences:**

- An authorized participant receives `200 OK` with sender, recipient, amount, currency, status, and timestamp.
- An unknown or unrelated Transaction ID returns `404 Not Found` to limit identifier enumeration.
- Internal persistence details and entity graphs are not serialized.

#### FR-13: Apply bounded pagination

Transaction history uses zero-based pagination with a default page size of 20 and a maximum page size of 100.

**Testable consequences:**

- Omitted parameters use `page=0` and `size=20`.
- Negative page values, non-positive sizes, and sizes above 100 return `400 Bad Request`.
- Responses include the `page`, `size`, `totalElements`, `totalPages`, and `last` metadata fields.
- MVP sorting is fixed to `createdAt` descending and then Transaction ID descending for deterministic ordering; no general-purpose sorting API is required.

**Feature acceptance:** AC-TXN-1 through AC-TXN-5.

### 8.5 API documentation

#### FR-14: Publish OpenAPI documentation

PayNest exposes generated OpenAPI/Swagger documentation for every MVP endpoint.

**Testable consequences:**

- Documentation includes request and response schemas, validation constraints, success statuses, representative errors, and bearer JWT security.
- Documentation distinguishes public Auth endpoints from protected endpoints.
- Example payloads contain no real credentials or secrets.

**Feature acceptance:** AC-QUALITY-1.

## 9. Business Rules

### 9.1 User rules

- BR-U1: Normalized email is required and unique.
- BR-U2: A password is required, securely hashed before persistence, and never recoverable from stored data.
- BR-U3: Authentication succeeds only when the submitted credentials match a persisted User; account states beyond existence are out of scope.
- BR-U4: Only an authenticated User may access protected Wallet and Transaction capabilities.
- BR-U5: Registration and Wallet creation form one business operation.

### 9.2 Wallet rules

- BR-W1: Every committed User has exactly one Wallet; every Wallet has exactly one User.
- BR-W2: A Wallet starts at USD `0.00`.
- BR-W3: Currency is fixed to `USD` and cannot be changed.
- BR-W4: No public API creates, deletes, funds, deposits to, or withdraws from a Wallet.
- BR-W5: Balance cannot be negative and is changed only by a successful Transfer or non-public test/development fixture setup.

### 9.3 Transfer and Transaction rules

- BR-T1: Sender is always the authenticated User.
- BR-T2: Recipient is resolved by Normalized email and must exist.
- BR-T3: Self-Transfer is prohibited.
- BR-T4: Transfer amount must be positive, exact to cents, and no greater than the Sender's available balance.
- BR-T5: Debit, credit, and Transaction creation either commit together or roll back together.
- BR-T6: A successful Transfer creates exactly one `COMPLETED` Transaction.
- BR-T7: A Transaction is visible only to its Sender and Recipient.
- BR-T8: Transaction direction is derived per viewer and is not stored.

## 10. Transaction and Consistency Requirements

### 10.1 Registration atomicity

User insertion and Wallet insertion run inside one database transaction. The transaction commits only after both succeed. A uniqueness or persistence failure rolls back both. The transaction boundary surrounds the complete registration business operation; architecture determines its exact Java placement.

### 10.2 Transfer atomicity

The complete Transfer business operation runs inside one database transaction that:

1. Resolves the authenticated Sender and Recipient.
2. Protects both affected Wallet rows with an appropriate PostgreSQL row-level concurrency strategy, using deterministic Wallet-ID ordering when acquiring locks.
3. Re-checks self-Transfer, amount, currency, and available balance while the rows are protected.
4. Debits the Sender Wallet.
5. Credits the Recipient Wallet.
6. Inserts one `COMPLETED` Transaction.
7. Commits.

Any exception before commit rolls back every step. No error path may catch and suppress an exception in a way that allows partial commit.

### 10.3 Concurrency strategy

The implementation must protect both affected Wallets with an appropriate PostgreSQL row-level concurrency strategy and enforce a `balance >= 0` database constraint. It must prevent overspending and lost updates, preserve deterministic lock ordering to reduce deadlocks, and roll back safely if a concurrency failure still occurs. Architecture decides the exact Spring/JPA or SQL mechanism; the PRD does not require a specific annotation or repository method. Distributed locks, message brokers, sagas, and eventual consistency are explicitly out of scope.

### 10.4 Failed Transfer records

A failed Transfer cannot create a durable Transaction as part of the rolled-back database transaction. The MVP therefore stores only `COMPLETED` Transactions. `FAILED` is not a persisted MVP status and must not be created in a separate transaction solely to populate Transaction history. Safe operational logging is sufficient for learning and diagnosis.

## 11. Domain Model

```text
User 1 ───────── 1 Wallet
                    │
                    ├──── 0..N outgoing Transactions (senderWalletId)
                    └──── 0..N incoming Transactions (receiverWalletId)
```

The MVP has exactly three core persisted domain entities: User, Wallet, and Transaction. Transfer is the atomic business operation that coordinates them; it is not a fourth persisted entity. Ledger, BalanceHistory, Currency, Payment, Account, AuditLog, Notification, and Recipient entities are not part of the MVP.

### 11.1 User ownership and purpose

User owns identity and credential data. Its one-to-one relationship with Wallet establishes that every registered account has one balance account. The Wallet row owns the foreign key to User, with a unique constraint enforcing one Wallet per User.

### 11.2 Wallet ownership and purpose

Wallet owns currency and current balance. It is the consistency boundary for balance updates. A Wallet can participate in many outgoing and incoming Transactions; each Transaction references exactly one sender Wallet and one receiver Wallet.

### 11.3 Transaction ownership and purpose

Transaction is an immutable transfer record owned by the Transaction module. Each Transaction references `senderWallet`, `receiverWallet`, `amount`, `status`, and `createdAt`; it does not duplicate balances. The relationships are required at the database level, but the object model need not expose large bidirectional collections merely because the conceptual cardinality is one-to-many.

### 11.4 JPA relationship learning requirements

- The User-to-Wallet relationship provides practical `@OneToOne` learning, including owning-side, foreign-key, unique-constraint, and cascade decisions.
- The two Transaction-to-Wallet references provide practical `@ManyToOne` learning for outgoing and incoming Transactions.
- The inverse one-to-many cardinalities provide `@OneToMany` learning value, but the implementation must not require large collections such as `wallet.getTransactions()` when bounded repository queries are more appropriate.
- Relationship design must address lazy loading, DTO mapping, cascade behavior, and avoidance of recursive entity serialization.
- No many-to-many relationship may be added solely for educational completeness.

These are mapping and design learning goals, not permission to add domain entities or expose persistence graphs through the API.

## 12. Modular Monolith Architecture

```text
Client
  │ REST/JSON + Bearer JWT
  ▼
PayNest Spring Boot application
  ├── Authentication module — credentials, JWT issuance and validation
  ├── User module           — User profile and registration identity
  ├── Wallet module         — Wallet state and balance rules
  ├── Transaction module    — Transaction persistence and participant queries
  └── Common                — error shape, shared configuration, narrow utilities
  │
  ▼
PostgreSQL — one schema/database and local ACID transactions
```

### 12.1 Required boundaries

- One Spring Boot application built with Maven and one PostgreSQL database.
- Organize packages by feature module, keeping controller, application service, domain, and persistence concerns within each feature.
- Authentication resolves identity but does not own Wallet rules.
- User owns profile and credential identity; the registration use case coordinates User and Wallet creation within one local transaction.
- Wallet owns balance state, debit/credit behavior, and invariants such as non-negative balance.
- Transaction owns the immutable transfer record and participant-facing queries.
- A focused application or domain service coordinates the Transfer use case across User, Wallet, and Transaction responsibilities: resolve parties, protect Wallet rows, validate rules, debit, credit, create the Transaction, and commit atomically. `TransferService` is an illustrative name, not a required class or additional domain entity.
- Controllers translate HTTP requests and responses; they must not contain business logic.
- Repositories provide persistence access; they must not orchestrate registration or Transfer business operations.
- Use clear in-process module boundaries. Add interfaces or abstractions only when they clarify an existing responsibility or testing need, not merely to imitate future microservices.
- No network calls, asynchronous messaging, duplicated databases, or distributed transaction patterns exist between modules.

### 12.2 Architectural guardrail

Future extractability is a reason for clear ownership, not a reason to simulate microservices inside the monolith. The MVP must prefer simple in-process collaboration and one local ACID transaction whenever correctness requires it. Exact package names, Java class names, service interfaces, repository methods, DTO organization, exception hierarchies, locking annotations, and migration-tool configuration are decided during architecture and implementation design.

## 13. API Requirements

All JSON uses UTF-8. Timestamps are ISO 8601 UTC instants. Monetary values are JSON numbers with no more than two fractional digits. Protected requests use `Authorization: Bearer <JWT>`.

### 13.1 Endpoint summary

| Feature | Method | URL | Authentication | Success |
|---|---|---|---|---|
| Sign up | `POST` | `/api/auth/signup` | Public | `201 Created` |
| Sign in | `POST` | `/api/auth/signin` | Public | `200 OK` |
| Current User | `GET` | `/api/users/me` | JWT | `200 OK` |
| Wallet balance | `GET` | `/api/wallet` | JWT | `200 OK` |
| Transfer | `POST` | `/api/wallet/transfers` | JWT | `201 Created` |
| Transaction history | `GET` | `/api/transactions?page=0&size=20` | JWT | `200 OK` |
| Transaction detail | `GET` | `/api/transactions/{id}` | JWT | `200 OK` |

### 13.2 Sign up

**Request**

```json
{
  "name": "Amina Rahman",
  "email": "amina@example.com",
  "password": "example-password"
}
```

**Response — `201 Created`**

```json
{
  "user": {
    "id": 101,
    "name": "Amina Rahman",
    "email": "amina@example.com",
    "createdAt": "2026-10-06T10:00:00Z"
  },
  "wallet": {
    "balance": 0.00,
    "currency": "USD",
    "createdAt": "2026-10-06T10:00:00Z"
  }
}
```

**Validation:** name, email, and password rules in Section 15.  
**Errors:** `400` invalid input, `409` duplicate email, `500` unexpected failure reported without internal details.

### 13.3 Sign in

**Request**

```json
{
  "email": "amina@example.com",
  "password": "example-password"
}
```

**Response — `200 OK`**

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-06T11:00:00Z"
}
```

**Validation:** email and password are required.  
**Errors:** `400` missing or malformed input, `401` invalid credentials.

### 13.4 Current User

**Request body:** none.

**Response — `200 OK`**

```json
{
  "id": 101,
  "name": "Amina Rahman",
  "email": "amina@example.com",
  "createdAt": "2026-10-06T10:00:00Z"
}
```

**Errors:** `401` invalid or missing JWT, including when the authenticated subject no longer resolves to a User.

### 13.5 Wallet balance

**Request body:** none.

**Response — `200 OK`**

```json
{
  "balance": 500.00,
  "currency": "USD",
  "createdAt": "2026-10-06T10:00:00Z"
}
```

**Errors:** `401` invalid or missing JWT, `404` Wallet not found.

### 13.6 Transfer money

**Request**

```json
{
  "recipientEmail": "rahim@example.com",
  "amount": 150.00
}
```

**Response — `201 Created`**

```json
{
  "transaction": {
    "id": 1001,
    "direction": "SENT",
    "counterparty": {
      "name": "Rahim Ahmed",
      "email": "rahim@example.com"
    },
    "amount": 150.00,
    "currency": "USD",
    "status": "COMPLETED",
    "createdAt": "2026-10-06T10:30:00Z"
  },
  "balanceAfter": 350.00
}
```

**Validation:** recipient and amount rules in Section 15.  
**Errors:** `400` malformed input, `401` invalid or missing JWT, `404` Recipient or Wallet not found, `422` self-Transfer or insufficient balance, `409` concurrency or data-integrity conflict for which a retry might be safe, `500` unexpected failure that caused the operation to roll back and was reported without internal details.

### 13.7 Transaction history

**Request body:** none. Query parameters are `page` and `size` as defined by FR-13.

**Response — `200 OK`**

```json
{
  "content": [
    {
      "id": 1001,
      "direction": "SENT",
      "counterparty": {
        "name": "Rahim Ahmed",
        "email": "rahim@example.com"
      },
      "amount": 150.00,
      "currency": "USD",
      "status": "COMPLETED",
      "createdAt": "2026-10-06T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

**Errors:** `400` invalid pagination, `401` invalid or missing JWT, `404` Wallet not found.

### 13.8 Transaction detail

**Request body:** none. Path parameter `id` is required and must be a positive supported identifier.

**Response — `200 OK`**

```json
{
  "id": 1001,
  "direction": "RECEIVED",
  "sender": {
    "name": "Amina Rahman",
    "email": "amina@example.com"
  },
  "recipient": {
    "name": "Rahim Ahmed",
    "email": "rahim@example.com"
  },
  "amount": 150.00,
  "currency": "USD",
  "status": "COMPLETED",
  "createdAt": "2026-10-06T10:30:00Z"
}
```

**Errors:** `400` invalid path parameter, `401` invalid or missing JWT, `404` unknown or inaccessible Transaction.

## 14. Authentication and Authorization

### 14.1 Registration flow

```text
Validate request → Normalize email → Hash password → Create User → Create Wallet → Commit
```

Sign-up does not automatically authenticate the new User; the client follows the explicit sign-in flow.

### 14.2 Sign-in flow

```text
Validate request → Find User → Verify password → Generate signed JWT → Return token and expiry
```

The JWT must use signing material supplied through external configuration. It must include a stable User identifier in the subject claim, an issued-at claim, and an expiration claim, and it must exclude password data and unnecessary personal data. The exact token lifetime is configurable and documented in application configuration rather than fixed as a product promise.

### 14.3 Request authorization

- Authentication endpoints are public; all other MVP endpoints require a valid JWT.
- Authorization is ownership-based. The authenticated subject determines the current User and Wallet.
- Transaction detail queries must include participant ownership in the data-access condition or perform an equivalent service-layer authorization check.
- A JWT whose subject no longer resolves to a User is rejected with `401 Unauthorized`.
- The MVP has no roles, permissions matrix, refresh tokens, revocation list, or session store.

## 15. Validation Rules

Validation uses Jakarta Bean Validation for request shape and service/domain checks for database-dependent business rules.

| Input | Rules |
|---|---|
| Sign-up `name` | Required, trimmed, 2–100 characters after trimming. |
| Sign-up `email` | Required, syntactically valid, maximum 254 characters, normalized before lookup, unique after normalization. |
| Sign-up `password` | Required, 8–72 characters; never trimmed or otherwise silently transformed. [ASSUMPTION A-2: Advanced composition rules are omitted to keep the learning MVP focused.] |
| Sign-in `email` | Required, syntactically valid, normalized before lookup. |
| Sign-in `password` | Required and verified with the configured password encoder. |
| Transfer `recipientEmail` | Required, syntactically valid, normalized, must identify a User other than the Sender. |
| Transfer `amount` | Required, greater than `0.00`, at most two fractional digits, supported by `NUMERIC(19,2)`, and no greater than available balance. |
| History `page` | Optional; integer ≥ 0; default 0. |
| History `size` | Optional; integer from 1 through 100; default 20. |

Malformed JSON, unknown enum values, type mismatches, and constraint violations return a consistent `400 Validation Error`. The API rejects extra fractional digits rather than silently rounding money.

## 16. Error Handling

All errors use one safe JSON structure:

```json
{
  "timestamp": "2026-10-06T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Transfer amount must be greater than zero",
  "path": "/api/wallet/transfers"
}
```

Field validation may add a `fieldErrors` object mapping field names to safe messages. Stack traces, SQL, class names, password data, JWT contents, and internal exception messages must never appear in client responses.

| Condition | HTTP | Error code | Required behavior |
|---|---:|---|---|
| Request validation failure | 400 | `VALIDATION_ERROR` | Return a safe field-level or request-level message. |
| Invalid credentials | 401 | `AUTHENTICATION_FAILED` | Use a generic message. |
| Missing/invalid/expired JWT | 401 | `INVALID_TOKEN` | Do not expose token validation internals. |
| Authenticated access not allowed | 403 | `ACCESS_DENIED` | Use only where resource existence is not sensitive. |
| User/Recipient not found | 404 | `USER_NOT_FOUND` | Do not reveal more than required by the requested operation. |
| Wallet not found | 404 | `WALLET_NOT_FOUND` | Log as a data-integrity concern without exposing internals. |
| Transaction unknown or unrelated | 404 | `TRANSACTION_NOT_FOUND` | Return the same response for unknown and inaccessible Transaction IDs. |
| Duplicate email | 409 | `EMAIL_ALREADY_EXISTS` | Return a stable, non-technical message. |
| Concurrent/data conflict | 409 | `CONFLICT` | Entire operation remains rolled back. |
| Self-Transfer | 422 | `SELF_TRANSFER_NOT_ALLOWED` | No state change. |
| Insufficient balance | 422 | `INSUFFICIENT_BALANCE` | No state change. |
| Unexpected Transfer/database failure | 500 | `TRANSACTION_FAILED` | Roll back and return a generic message. |
| Other unexpected failure | 500 | `INTERNAL_ERROR` | Write a correlatable server log and return a safe client response. |

A centralized exception-handling component must map known exceptions consistently. Authentication failures handled by Spring Security must use the same overall error shape where practical.

## 17. Database Requirements

PostgreSQL is the only MVP database. Schema changes must be versioned through a migration tool; runtime auto-update may be used only for disposable local experimentation, not as the normal project workflow.

### 17.1 `users`

| Column | Requirement |
|---|---|
| `id` | `BIGINT` generated primary key. [ASSUMPTION A-3: Generated numeric IDs are sufficient for this learning MVP.] |
| `name` | `VARCHAR(100) NOT NULL`. |
| `email` | `VARCHAR(254) NOT NULL`, stored in normalized form, with a unique constraint. |
| `password_hash` | `VARCHAR(255) NOT NULL`. |
| `created_at` | Time-zone-aware timestamp, `NOT NULL`. |

The unique email constraint supplies the index needed for sign-in and Recipient lookup.

### 17.2 `wallets`

| Column | Requirement |
|---|---|
| `id` | `BIGINT` generated primary key. |
| `user_id` | Foreign key to `users(id)`, `NOT NULL`, `UNIQUE`. |
| `balance` | `NUMERIC(19,2) NOT NULL DEFAULT 0.00`, check `balance >= 0`. |
| `currency` | `CHAR(3) NOT NULL DEFAULT 'USD'`, check value is `USD`. |
| `created_at` | Time-zone-aware timestamp, `NOT NULL`. |

### 17.3 `transactions`

| Column | Requirement |
|---|---|
| `id` | `BIGINT` generated primary key. |
| `sender_wallet_id` | Foreign key to `wallets(id)`, `NOT NULL`. |
| `receiver_wallet_id` | Foreign key to `wallets(id)`, `NOT NULL`. |
| `amount` | `NUMERIC(19,2) NOT NULL`, check `amount > 0`. |
| `status` | Constrained text or enum, `NOT NULL`; the only MVP value is `COMPLETED`. |
| `created_at` | Time-zone-aware timestamp, `NOT NULL`. |

Required indexes support participant history ordered by creation time and Transaction ID, such as `(sender_wallet_id, created_at DESC, id DESC)` and `(receiver_wallet_id, created_at DESC, id DESC)`. A check constraint prevents equal sender and receiver Wallet IDs. Transaction rows are immutable through the application.

### 17.4 Monetary and time representation

- Java uses `BigDecimal`; floating-point types are prohibited for money.
- Arithmetic preserves a scale of 2 and exact cents; requests with a greater scale are rejected.
- PostgreSQL uses `NUMERIC(19,2)` for balances and amounts.
- Application timestamps use UTC instants; the database uses time-zone-aware timestamps.

## 18. Security Requirements

- Store passwords only through a modern adaptive one-way password encoder supported by Spring Security.
- Keep JWT signing material, database credentials, and other secrets outside source control and logs.
- Deny protected requests by default unless a valid JWT establishes an authenticated User.
- Derive ownership from authenticated identity; do not trust caller-supplied User or Wallet IDs.
- Use DTOs so entities, password hashes, and internal relationships are not serialized.
- Apply input validation at the request boundary and enforce critical invariants again in service/database layers.
- Use generic authentication errors to reduce account enumeration.
- Return `404` for unrelated Transaction IDs to reduce direct-object-reference leakage.
- Do not log passwords, bearer tokens, full JWTs, password hashes, or database secrets.
- Use parameterized persistence operations through JPA/repositories; never construct SQL from untrusted input.
- Configure CORS only if a separate client is later introduced; permissive wildcard CORS is not an MVP requirement.
- Production-profile transport is expected to be HTTPS at the deployment boundary, though TLS termination infrastructure is outside this repository's MVP.

## 19. Testing Requirements

Tests prioritize business behavior, security boundaries, and rollback guarantees. They must include unit tests for focused domain and service rules and integration tests across HTTP, security, persistence, and a PostgreSQL instance. Rollback and competing-Transfer tests must not use an in-memory database whose locking behavior differs from PostgreSQL.

### 19.1 Authentication and User

- Successful registration returns a safe User and creates exactly one Wallet.
- Initial Wallet balance is USD `0.00`.
- Duplicate email, including case variation after normalization, is rejected.
- Concurrent duplicate registration commits at most one User.
- Invalid credentials are rejected with a generic error.
- Successful sign-in returns a usable JWT.
- Current-User lookup returns only the authenticated profile.

### 19.2 Wallet and Transfer

- An authenticated User can view only their Wallet.
- Successful Transfer changes Sender and Recipient balances by the exact amount and creates one Transaction.
- Zero, negative, over-precision, missing, and oversized amounts are rejected.
- Unknown Recipient and self-Transfer are rejected.
- Insufficient balance changes no state.
- A forced failure after debit but before commit rolls back both balances and the Transaction.
- Competing Transfers cannot overdraw a Wallet or lose a committed update.

### 19.3 Transaction history and security

- Sender history includes the Transaction as `SENT`; Recipient history includes it as `RECEIVED`.
- History excludes every Transaction in which the authenticated User's Wallet is neither Sender nor Recipient.
- Newest-first pagination and metadata are correct, including empty and final pages.
- Either participant can view detail.
- An unrelated User cannot view Transaction detail and receives `404`.
- Anonymous access and invalid, tampered, and expired JWTs are rejected.

### 19.4 Test-data requirement

Because all real registrations start at zero and deposits are out of scope, automated tests and local demonstrations may fund Wallets through SQL seed data, SQL fixtures, test fixtures, test-only helpers, or another explicitly non-production development mechanism. This setup exists only to make the Send Money use case demonstrable and testable. It is not a product feature and must never be exposed as a normal production-profile REST endpoint.

## 20. Non-Functional Requirements

### 20.1 Maintainability and modularity

- Organize application code by feature module rather than one global controller/service/repository bucket.
- Keep business rules in application or domain services, not controllers or persistence callbacks.
- Keep persistence access in repositories and business-operation orchestration outside repositories.
- Coordinate cross-module use cases through focused in-process application logic without requiring speculative abstraction layers.
- Common contains genuinely cross-cutting concerns only and must not become a miscellaneous domain module.

### 20.2 Reliability and consistency

- Database constraints backstop critical application invariants.
- Known errors have deterministic HTTP mappings.
- Transfer and registration rollback behavior is integration-tested.

### 20.3 Performance proportional to scope

- All list endpoints are bounded by pagination.
- Participant and time indexes support the primary Transaction query.
- A 20-item history request must execute as a bounded database query and must not load the full Transaction history into application memory. No public latency or throughput SLA is promised.

### 20.4 Observability

- Log application startup, safe authentication outcomes, Transfer success/failure category, and unexpected exceptions at appropriate levels.
- Logs must not contain secrets or full sensitive request payloads.
- Metrics platforms, distributed tracing, and alerting infrastructure are not required.

### 20.5 Configuration and portability

- Environment-specific values use external configuration.
- The project runs directly against a developer-managed PostgreSQL database.
- Automated integration tests use a separate developer-managed `paynest_test` PostgreSQL database and must verify that exact database name before destructive setup.
- Docker, Docker Compose, Testcontainers, and container-based delivery/testing are outside the PayNest MVP and learning path.
- A clean setup path and sample non-secret environment configuration are documented.

## 21. Development Phases

Features should be implemented in learning order rather than simply following PRD section order:

```text
Foundation
→ User
→ User/Wallet relationship
→ Registration + atomic Wallet creation
→ Authentication/JWT
→ Wallet
→ Transfer
→ Transaction history
→ Concurrency hardening
→ Testing
→ OpenAPI
→ Direct local verification
```

This sequence is a learning plan, not an additional product requirement. Direct local verification completes the MVP without a container-based delivery phase.

### Phase 1 — Project foundation

- Create the Spring Boot Maven project, PostgreSQL configuration, initial modular structure, database migration baseline, and Git setup.
- Establish local configuration without committing secrets.

### Phase 2 — User

- Implement User persistence, core validation, normalized email uniqueness, and password hashing.
- Keep this phase focused on the User model and persistence behavior; authentication token flow follows after registration is complete.

### Phase 3 — User/Wallet relationship and registration

- Implement Wallet persistence and the one-to-one User/Wallet relationship.
- Complete registration as one atomic User-plus-Wallet operation with a zero USD balance and rollback tests.

### Phase 4 — Authentication and Wallet access

- Implement sign-in, JWT security, current User access, consistent authentication errors, and the Wallet balance endpoint.

### Phase 5 — Money Transfer and Transaction record

- Implement Recipient lookup, business validation, debit/credit behavior, completed Transaction persistence, and the atomic Transfer transaction boundary.
- Prove rollback behavior before treating the baseline Transfer feature as complete.

### Phase 6 — Transaction history and concurrency hardening

- Implement participant query, relative direction mapping, detail authorization, newest-first pagination, and sorting.
- Apply the selected PostgreSQL row-level concurrency strategy and test deterministic lock ordering, competing Transfers, and negative-balance protection.

### Phase 7 — Testing, errors, and API documentation

- Complete global exception handling, unit and PostgreSQL-backed integration tests, OpenAPI documentation, safe logging, and configuration/documentation cleanup.

### Phase 8 — Direct local verification

- Document local PostgreSQL prerequisites, development and test database setup, required environment variables, direct Spring Boot startup, test execution, fixture funding, OpenAPI access, and shutdown.
- Verify a clean direct startup applies Flyway migrations, passes Hibernate validation, and supports registration, sign-in, Wallet read, seeded Transfer, and history flow.

## 22. Future Microservice Evolution

The implemented MVP remains a simple in-process modular monolith. Future architecture must not influence the MVP through speculative abstractions, network boundaries, messaging, duplicated data, or preparatory infrastructure.

```text
MVP:
Modular Monolith

Future learning:
Microservice extraction
API Gateway
Kafka
Separate databases
Inter-service communication
Distributed consistency
```

Only after the learning goals and monolith are stable may the module boundaries guide a separate extraction exercise:

```text
API Gateway
  ├── Auth Service
  ├── User Service
  ├── Wallet Service
  └── Transaction Service
```

- **Auth Service candidate:** token issuance and credential authentication can be isolated once multiple services need shared identity.
- **User Service candidate:** profile lifecycle is conceptually distinct from money movement.
- **Wallet Service candidate:** balance ownership and debit/credit consistency form the most sensitive transactional boundary.
- **Transaction Service candidate:** participant history and immutable records may scale and evolve separately.

Extraction would force explicit decisions about duplicated identity data, service-owned databases, inter-service authorization, synchronous REST versus events, and how Transfer consistency works without one local transaction. Spring Cloud Gateway, service discovery, Kafka, separate databases, event-driven communication, and distributed consistency are learning topics for that later phase only. They are not MVP dependencies, acceptance criteria, abstractions, or preparatory infrastructure.

## 23. Risks and Edge Cases

| Risk or edge case | MVP response |
|---|---|
| Every new Wallet has zero funds and no deposit feature exists | Accept this deliberate demo limitation; use test/local fixture funding only, never a public funding API. |
| Two concurrent Transfers spend the same balance | Lock both Wallet rows, re-check balance under lock, and enforce a non-negative database constraint. |
| Opposite-direction Transfers deadlock | Lock affected Wallets in deterministic ID order and roll back safely on residual conflicts. |
| User email differs only by case or whitespace | Normalize before persistence and enforce database uniqueness on the normalized value. |
| Two concurrent registrations pass an application pre-check | Treat the database unique constraint as authoritative and map the violation to `409`. |
| Client sends `0.001`, floating-point artifacts, or an amount beyond precision | Parse as decimal, validate scale and precision, reject rather than round. |
| Recipient disappears or data becomes inconsistent during Transfer | Resolve and lock within the transaction; roll back and return a safe error. |
| A failed Transfer is expected to appear as a durable history record | Do not persist it in MVP; rollback semantics take priority over a misleading `FAILED` row. |
| User guesses another Transaction ID | Check ownership and return the same `404` response as for an unknown ID. |
| JWT is expired, tampered with, or references a deleted User | Reject with `401`; never trust unverified claims or treat an unresolved subject as authenticated. |
| Entity relationships cause recursive/oversized JSON | Return dedicated DTOs and never serialize persistence entities directly. |
| Transaction table grows | Use indexes, fixed newest-first sorting, and bounded pagination. |
| Unexpected exception occurs after a balance mutation | Let the transactional boundary roll back; do not swallow the exception. |

## 24. Acceptance Criteria

### 24.1 Authentication and User

- **AC-AUTH-1:** Given valid unique registration data, when a client signs up, then one User and one USD Wallet are committed and the API returns `201` without password data.
- **AC-AUTH-2:** Given that Wallet persistence fails, when sign-up is attempted, then the User insert is rolled back.
- **AC-AUTH-3:** Given an existing Normalized email, when sign-up is attempted again, then the API returns `409` and no duplicate User or Wallet exists.
- **AC-AUTH-4:** Given valid credentials, when the User signs in, then a signed expiring bearer JWT is returned.
- **AC-AUTH-5:** Given an unknown email or wrong password, when sign-in is attempted, then the same generic `401` shape is returned.
- **AC-AUTH-6:** Given a valid JWT, when `/api/users/me` is requested, then only that User's safe profile is returned.
- **AC-AUTH-7:** Given no, invalid, tampered, or expired JWT, when any protected endpoint is requested, then access is rejected with `401`.

### 24.2 Wallet

- **AC-WALLET-1:** Given a newly committed User, exactly one Wallet exists with balance `0.00` and currency `USD`.
- **AC-WALLET-2:** Given an authenticated User, when `/api/wallet` is requested, then that User's exact balance and currency are returned.
- **AC-WALLET-3:** There is no public endpoint to create, delete, fund, deposit to, or withdraw from a Wallet.
- **AC-WALLET-4:** Database constraints reject a second Wallet for one User and any negative balance.

### 24.3 Transfer

- **AC-TRANSFER-1:** Given a funded Sender and existing Recipient, when a valid Transfer is submitted, then the Sender is debited, the Recipient is credited, one `COMPLETED` Transaction is created, and `201` is returned.
- **AC-TRANSFER-2:** Given a missing amount or an amount that is zero, negative, too precise, or beyond the supported precision, when the Transfer is submitted, then the request is rejected and no state changes occur.
- **AC-TRANSFER-3:** Given insufficient balance, an unknown Recipient, or a self-Transfer, when the Transfer is submitted, then the request is rejected and no state changes occur.
- **AC-TRANSFER-4:** Given a failure after either balance has changed but before the transaction commits, both balance changes and the Transaction insert roll back.
- **AC-TRANSFER-5:** Given competing Transfers whose sum exceeds the starting balance, when they execute concurrently, then no committed outcome makes the Sender negative and at most the affordable Transfers succeed.
- **AC-TRANSFER-6:** Given a successful Transfer of `150.00` from `500.00` to a Recipient with `100.00`, the committed balances are exactly `350.00` and `250.00`.

### 24.4 Transactions

- **AC-TXN-1:** Given a completed Transfer, when the Sender requests history, then it appears as `SENT`; when the Recipient requests history, then it appears as `RECEIVED`.
- **AC-TXN-2:** Given a Transaction history larger than one page, when `page` and `size` are supplied, then bounded newest-first results and correct metadata are returned.
- **AC-TXN-3:** Given that either participant requests Transaction detail, the safe detail response is returned.
- **AC-TXN-4:** Given an unrelated User requests the same Transaction ID, then `404` is returned and no Transaction data is disclosed.
- **AC-TXN-5:** Given a failed Transfer, no Transaction record is persisted and nothing from that attempt appears in either history.

### 24.5 Quality and delivery

- **AC-QUALITY-1:** OpenAPI describes all endpoints, schemas, bearer authentication, and expected errors.
- **AC-QUALITY-2:** Unit and integration tests cover the behaviors listed in Section 19 without relying on an arbitrary coverage target.
- **AC-QUALITY-3:** Given a clean developer-managed PostgreSQL database, direct local startup runs the monolithic application, applies Flyway migrations, and passes Hibernate schema validation.
- **AC-QUALITY-4:** Repository inspection shows one Spring Boot application, one PostgreSQL database, exactly three core persisted domain entities (User, Wallet, Transaction), feature modules, and no microservice infrastructure.
- **AC-QUALITY-5:** Client errors and application logs expose no plaintext passwords, hashes, bearer tokens, signing secrets, SQL details, or stack traces.

## 25. Assumptions and Open Questions

### 25.1 Assumptions index

- **A-1 (Section 8.3):** `recipientEmail` is the Transfer Recipient identifier because email is already unique and avoids exposing or requiring Wallet IDs.
- **A-2 (Section 15):** Password length is 8–72 characters; advanced password-composition rules are intentionally omitted from this learning MVP.
- **A-3 (Section 17.1):** Public identifiers use generated `BIGINT` values for simplicity; changing to UUIDs later would not alter the business behavior.
- **A-4 (Section 7):** Test and local fixture funding is acceptable for demonstrating Transfers while all public funding capabilities remain out of scope.
- **A-5 (Section 8.4):** Counterparty name and email are appropriate Transaction receipt data for the two participating Users; no unrelated User can access them.

These assumptions are non-blocking for implementation and should be revisited only if product scope introduces a UI, public deployment, or external identity requirements.

### 25.2 Open questions

None block MVP implementation. Configuration values such as JWT lifetime and local seed strategy must be documented by the implementation but do not change the product contract.
