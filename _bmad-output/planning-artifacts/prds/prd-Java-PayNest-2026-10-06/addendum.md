# PayNest PRD Addendum

This addendum preserves technical decisions and rationale that support the PayNest PRD without adding product scope.

## A. Decision Rationale

### A.1 Modular monolith before microservices

One application and one database let the developer learn Spring Boot, domain boundaries, REST, persistence, security, and local ACID transactions without introducing network failure modes, eventual consistency, service deployment, or operational overhead. Clear package and ownership boundaries preserve the option to extract services later.

### A.2 PostgreSQL concurrency protection for Transfers

The MVP requires database-level protection against simultaneous overspending and lost updates. Both affected Wallet rows must be protected, and any locks must be acquired in deterministic Wallet-ID order to reduce deadlocks. Architecture may select `@Lock`, `PESSIMISTIC_WRITE`, explicit SQL, or another appropriate PostgreSQL row-level mechanism and must document its failure and retry behavior. The product requirement is the consistency guarantee, not a specific annotation. Distributed locking is unnecessary in one application with one database.

### A.3 Persist only completed Transactions

The Transaction row is created in the same database transaction as the debit and credit. A failed operation rolls back that insert, so a durable `FAILED` row would require a second, independent transaction and a more complex audit model. For this learning scope, the REST error and safe logs describe failures while Transaction history remains a record of completed Transfers.

### A.4 Funding exists only as test/local data

The requested product begins every Wallet at zero and explicitly excludes deposits, withdrawals, bank integration, payment gateways, cards, and admin tools. A public funding mechanism would violate that boundary. Automated tests and local demonstrations may arrange balance state through SQL seed data, SQL fixtures, test fixtures, test-only helpers, or another explicitly non-production mechanism. Production-profile APIs must never expose this capability.

### A.5 Database constraints complement service validation

Service validation produces useful errors, while database constraints protect invariants under concurrency or programming mistakes. Unique email, one Wallet per User, positive Transaction amount, distinct Sender and Recipient Wallets, fixed USD currency, and non-negative balance all earn database enforcement because violating them would corrupt the core learning example.

## B. Implementation Responsibility Guidance (Non-binding)

Architecture should choose the exact packages, class names, interfaces, repositories, DTO organization, and annotations. Whatever shape it selects must preserve these responsibilities:

- Controllers translate HTTP requests and responses and contain no business logic.
- A focused application or domain service coordinates the complete Transfer use case; `TransferService` is an example name, not a prescribed type.
- The Wallet module owns balance state, debit/credit behavior, and non-negative-balance rules.
- The Transaction module owns completed Transaction persistence and participant queries.
- Repositories provide persistence access and do not orchestrate registration or Transfers.
- Transaction boundaries surround the registration and Transfer business operations.
- Common code contains narrowly shared mechanics, not core business concepts.

No interface, pattern, or abstraction is required solely to make later microservice extraction look easier.

## C. Recommended Tooling (Non-binding)

- Spring Boot with Maven.
- Spring Web, Spring Data JPA, Spring Security, Jakarta Validation.
- A well-maintained JWT library compatible with the selected Spring Boot version.
- PostgreSQL with Flyway or Liquibase migrations.
- springdoc-openapi for Swagger/OpenAPI.
- JUnit 5, Spring Boot Test, security test utilities, and PostgreSQL-backed integration tests against a dedicated developer-managed `paynest_test` database.
- Direct Spring Boot startup against developer-managed PostgreSQL, with Flyway migration and Hibernate validation evidence.

Docker, Docker Compose, Testcontainers, and container-based delivery/testing are explicitly outside scope. Test setup must never delete data or reset a schema until `select current_database()` returns exactly `paynest_test`.

Exact dependency versions belong in the build and architecture records, not the product requirements.

## D. Deferred Alternatives

- A separate immutable attempt or audit entity that can represent failed Transfers.
- UUID public identifiers.
- Refresh tokens, logout and token revocation, account lockout, and rate limiting.
- Idempotency keys for client retry safety.
- A genuine funding/deposit workflow.
- Ledger accounting with debit and credit entries instead of mutable Wallet balances.

Each alternative may be useful in a production-oriented successor, but none is required to meet this MVP's learning goals.
