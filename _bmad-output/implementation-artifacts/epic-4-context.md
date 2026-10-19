# Epic 4 Context: Documented and Reproducible MVP Delivery

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Deliver the completed PayNest modular monolith as a safe, discoverable, and reproducible learning project: API consumers can rely on one stable error contract and generated documentation for exactly seven product operations, while developers can start and verify the application directly against developer-managed PostgreSQL using externalized, non-secret configuration. This final epic hardens and documents the behavior delivered by the first three epics without adding product endpoints, persisted entities, container tooling, or distributed infrastructure.

## Stories

- Story 4.1: Finalize Safe Errors, Logging, and Configuration
- Story 4.2: Publish and Verify the OpenAPI Contract
- Story 4.3: Document and Verify Direct Local Startup

## Requirements & Constraints

- Every client-visible failure uses `{timestamp, status, error, message, path}` with an optional field-to-message `fieldErrors` object. Controller advice and Spring Security authentication/authorization handlers must preserve the same overall shape.
- Use only the stable codes `VALIDATION_ERROR`, `AUTHENTICATION_FAILED`, `INVALID_TOKEN`, `ACCESS_DENIED`, `USER_NOT_FOUND`, `WALLET_NOT_FOUND`, `TRANSACTION_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `CONFLICT`, `SELF_TRANSFER_NOT_ALLOWED`, `INSUFFICIENT_BALANCE`, `TRANSACTION_FAILED`, and `INTERNAL_ERROR`, with deterministic HTTP mappings. Unknown and inaccessible transactions share the same `404`; deadlock and lock-timeout conflicts use `409`; business rejections use `422`; unexpected transfer/database failures use `500` after rollback.
- Responses and logs must not disclose stack traces, SQL or driver/constraint details, Java class or entity internals, plaintext passwords, password hashes, bearer tokens, JWT claims or signing material, database credentials, or full sensitive request payloads. Log safe startup, authentication outcome, transfer outcome category, and unexpected-failure diagnostics at appropriate levels.
- Generated OpenAPI documentation must describe exactly these product operations: `POST /api/auth/signup`, `POST /api/auth/signin`, `GET /api/users/me`, `GET /api/wallet`, `POST /api/wallet/transfers`, `GET /api/transactions`, and `GET /api/transactions/{id}`. Documentation assets are not product operations.
- Each operation documents request parameters/body, validation constraints, exact success status and DTO schema, and representative shared errors. Preserve decimal money semantics; document history defaults (`page=0`, `size=20`), bounds (`page >= 0`, `1 <= size <= 100`), and positive transaction IDs. Publish DTOs only, never persistence entities or relationships. Examples use invented identities and placeholder tokens.
- OpenAPI and Swagger UI are public; signup and signin declare no security, while the remaining five operations require bearer JWT. Documentation access must not weaken protection of product operations.
- The local runbook must cover Java 25, Maven Wrapper 3.10.0, PostgreSQL 18.6, creation of separate `paynest` and `paynest_test` databases, required environment variables, direct startup, test execution, manual fixture funding, OpenAPI access, the complete signup-to-transaction smoke journey, and orderly shutdown.
- Final acceptance uses real developer-managed PostgreSQL. Integration tests must guard that `select current_database()` is exactly `paynest_test` before cleanup or schema reset. H2, Docker, Compose, Testcontainers, images, service connections, and container-based delivery/testing are out of scope.
- A clean `paynest` database must be migrated by Flyway at startup and validated by Hibernate. Funding remains a manual local/test fixture only; no funding migration or production-profile REST endpoint may be introduced.

## Technical Decisions

- Remain one Maven-built Spring Boot 4.1.1 application, one deployable unit, and one runtime PostgreSQL database. Keep exactly three persisted entities and seven product endpoints; do not add microservice, messaging, cache, cloud-secret, distributed-tracing, metrics-platform, or correlation-ID infrastructure.
- Use `springdoc-openapi` 3.1.1 with one `bearerAuth` security scheme of type HTTP, scheme `bearer`, format `JWT`. Group operations as Authentication, User, Wallet, and Transactions; the transfer route remains under Wallet.
- `GlobalExceptionHandler` handles controller/application failures; the JWT entry point and access-denied handler share the safe error writer for failures before controller advice. Keep the unchecked exception set small and meaningful, translate known persistence conflicts, and never return raw database messages.
- Externalize development database URL/user/password, test database URL/user/password, JWT secret, JWT lifetime, and PostgreSQL lock wait through `PAYNEST_DB_URL`, `PAYNEST_DB_USERNAME`, `PAYNEST_DB_PASSWORD`, `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, `PAYNEST_TEST_DB_PASSWORD`, `PAYNEST_JWT_SECRET`, `PAYNEST_JWT_TTL`, and `PAYNEST_DB_LOCK_TIMEOUT`. The local/test lock-timeout default is five seconds and no automatic retry is allowed. Committed YAML and `.env.example` contain only safe defaults or obvious placeholders; real `.env` files stay untracked.
- Flyway owns append-only schema evolution and runs for local and test profiles; Hibernate uses `ddl-auto=validate`. Production migrations contain no fixture data. Local funding uses `scripts/dev/fund-wallet.sql`; automated funding helpers stay in test scope.
- PostgreSQL-backed contract tests verify representative validation, security, not-found, conflict, business-rule, transfer-failure, and unexpected-error responses, including non-disclosure. A generated-spec integration test parses the document and asserts exactly the seven method/path pairs, public/protected distinction, bearer scheme, validation metadata, shared error schema, and success statuses; it must fail when an undocumented product endpoint appears.

## Cross-Story Dependencies

- Epic 4 assumes Epics 1-3 have completed the seven controllers, DTO contracts, JWT protection, transfer behavior, and transaction queries. Story 4.2 documents and tests those finished contracts rather than defining replacement behavior.
- Story 4.1 establishes the final error vocabulary, shared security error rendering, safe logging, and external configuration consumed by both the OpenAPI contract in Story 4.2 and the runbook/smoke verification in Story 4.3.
- Story 4.3 is the final delivery gate: it depends on the error and documentation hardening, all Flyway migrations, the dedicated PostgreSQL test suite, and the local-only funding helper being complete and consistent.
