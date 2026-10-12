# PayNest PRD Source Reconciliation

## Verdict

The finalized PRD is internally consistent and provides a sufficient binding contract for architecture. The architecture must implement a learning-first, package-by-feature modular monolith with one Spring Boot application, one PostgreSQL database, exactly three persisted domain entities, seven REST endpoints, local ACID transactions, JWT authentication, PostgreSQL-backed concurrency safety, ownership-protected reads, and no speculative microservice infrastructure. No source conflict requires a PRD change before architecture work begins.

## Binding architecture constraints

| Constraint | Source mapping |
|---|---|
| One Maven-built Spring Boot application, one deployment unit, one PostgreSQL database/schema, and local ACID transactions. Modules collaborate in-process only. | G-4, G-5; Section 12; AC-QUALITY-3, AC-QUALITY-4 |
| Organize by Authentication, User, Wallet, Transaction, and narrow Common feature boundaries; keep controllers free of business logic and repositories free of use-case orchestration. | G-4; Section 12.1; Section 20.1; AC-QUALITY-4 |
| A focused application/domain service coordinates Transfer across modules; Wallet owns balance rules and Transaction owns record persistence/query behavior. `TransferService` is illustrative, not mandated. | FR-8, FR-9, FR-10; BR-T5, BR-T6; Section 12.1 |
| Exactly three core persisted entities: User, Wallet, Transaction. Transfer is a business operation, not an entity. No Ledger, BalanceHistory, Currency, Payment, Account, AuditLog, Notification, or Recipient entity. | Section 11; AC-QUALITY-4 |
| `User 1:1 Wallet`; Wallet owns the unique `user_id` foreign key. Each Transaction has exactly one sender Wallet and one receiver Wallet, yielding conceptual outgoing/incoming `1:N` relationships. | FR-2, FR-10; BR-W1; Sections 11.1-11.4; AC-WALLET-1, AC-WALLET-4 |
| Preserve JPA learning value for `@OneToOne`, `@ManyToOne`, conceptual `@OneToMany`, owning sides, cascade/fetch choices, DTO mapping, and recursive-serialization avoidance, without requiring large bidirectional Transaction collections or any many-to-many mapping. | Section 11.4; FR-12; Security Section 18 |
| Registration is one business transaction: normalize email, hash password, insert User, insert exactly one zero-balance USD Wallet, then commit; any failure rolls back both. Exact Java annotation placement belongs to architecture. | FR-1, FR-2; BR-U1, BR-U2, BR-U5, BR-W1-W3; Section 10.1; AC-AUTH-1-3, AC-WALLET-1 |
| Transfer is one business transaction: resolve authenticated Sender and Recipient, protect both Wallet rows, re-check rules, debit, credit, insert one `COMPLETED` Transaction, then commit. Any exception before commit rolls back all state. | FR-7-FR-10; BR-T1-T6; Sections 10.2-10.4; AC-TRANSFER-1-6, AC-TXN-5 |
| Architecture must select an appropriate PostgreSQL row-level concurrency mechanism. It must prevent lost updates, concurrent overspending, and negative balances; protect both Wallets in deterministic Wallet-ID order; and roll back residual conflicts safely. No distributed locking. | FR-9; Section 10.3; SM-3; AC-TRANSFER-5; AC-WALLET-4 |
| Authentication uses Spring Security plus signed, expiring JWT bearer tokens. Auth endpoints are public; every other endpoint is protected. Identity and ownership derive from the verified JWT subject, never caller-supplied User/Wallet IDs. | FR-3-FR-6, FR-11, FR-12; BR-U3, BR-U4, BR-T1, BR-T7; Section 14; AC-AUTH-4-7, AC-TXN-4 |
| Use DTOs rather than serialized entities. Responses must not expose password data, persistence graphs, internal IDs beyond the contracted public identifiers, JWT contents, or technical internals. | FR-1, FR-5, FR-12; Sections 13, 16, 18; AC-QUALITY-5 |
| Use generated `BIGINT` identifiers, Java `BigDecimal`, PostgreSQL `NUMERIC(19,2)`, two-decimal exact arithmetic, and UTC/time-zone-aware timestamps. Reject excess monetary precision; never use floating point or silently round. | FR-7; Section 15; Section 17; AC-TRANSFER-2, AC-TRANSFER-6 |
| Schema changes are versioned through a migration tool. Docker Compose is a later delivery phase with only the app and PostgreSQL; it is not a prerequisite for early feature work. | Section 17; Phase 1, Phase 8; AC-QUALITY-3 |

## Seven endpoint contract

Exactly these endpoints exist in the MVP; architecture must not add others.

| Method and path | Access | Success | Binding behavior / source |
|---|---|---:|---|
| `POST /api/auth/signup` | Public | `201` | Name/email/password input; safe User plus zero-USD Wallet response; atomic creation; `400`, `409`, safe `500`. FR-1, FR-2; AC-AUTH-1-3 |
| `POST /api/auth/signin` | Public | `200` | Normalized email/password input; bearer JWT, token type, expiry; generic `401` for unknown email or wrong password. FR-3; AC-AUTH-4, AC-AUTH-5 |
| `GET /api/users/me` | JWT | `200` | Safe current profile derived from authenticated subject; unresolved subject is `401`. FR-4, FR-5; AC-AUTH-6, AC-AUTH-7 |
| `GET /api/wallet` | JWT | `200` | Current User's balance/currency/creation time; no caller-supplied User or Wallet ID. FR-6; AC-WALLET-2 |
| `POST /api/wallet/transfers` | JWT | `201` | `recipientEmail` plus `amount`; returns completed Transaction view and Sender `balanceAfter`; atomic debit/credit/record creation. FR-7-FR-10; AC-TRANSFER-1-6 |
| `GET /api/transactions?page=0&size=20` | JWT | `200` | Participant-only history; zero-based pagination, default 20, max 100; fixed `createdAt DESC, id DESC`; direction derived as `SENT`/`RECEIVED`. FR-11, FR-13; AC-TXN-1, AC-TXN-2 |
| `GET /api/transactions/{id}` | JWT | `200` | Positive BIGINT ID; participant-only safe detail; unknown and unrelated both return `404`. FR-12; AC-TXN-3, AC-TXN-4 |

## Data invariants

- **User:** generated `BIGINT` PK; trimmed name length 2-100; normalized lowercase/trimmed email, max 254, `NOT NULL UNIQUE`; password stored only as an adaptive one-way hash; `created_at NOT NULL`. (FR-1; BR-U1-U3; Section 15; Section 17.1)
- **Wallet:** generated `BIGINT` PK; `user_id NOT NULL UNIQUE` FK; `balance NUMERIC(19,2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0)`; `currency CHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD')`; timestamp required. A committed User owns exactly one Wallet. (FR-2, FR-6; BR-W1-W5; Section 17.2; AC-WALLET-1, AC-WALLET-4)
- **Transaction:** generated `BIGINT` PK; required sender/receiver Wallet FKs; positive `NUMERIC(19,2)` amount; only `COMPLETED` status; timestamp required; sender and receiver must differ; application treats rows as immutable. (FR-10; BR-T3-T6; Section 17.3; AC-TXN-5)
- **History indexes:** participant/time/id indexes are required for bounded newest-first queries, e.g. sender and receiver variants on `(wallet_id, created_at DESC, id DESC)`. (FR-11, FR-13; Section 17.3; Section 20.3)
- **Direction:** `SENT` or `RECEIVED` is calculated relative to the authenticated viewer and is not persisted. (FR-11; BR-T8; AC-TXN-1)
- **Failed transfers:** no durable Transaction row of any status. `FAILED`, `PENDING`, `REVERSED`, and similar states are outside the MVP. (FR-10; Section 10.4; AC-TXN-5)
- **Funding:** all real registrations start at `0.00`; local/test funding may use SQL fixtures, seed data, or test-only helpers. It is never a normal production-profile endpoint or product capability. (BR-W2, BR-W4, BR-W5; Section 19.4; AC-WALLET-3)

## Security, consistency, error, and test requirements

### Security

- Passwords use a modern Spring Security adaptive one-way encoder; secrets and JWT signing material are externalized and never logged. (FR-1, FR-3; BR-U2; Section 18; AC-QUALITY-5)
- JWT contains a stable User ID subject, issued-at, and expiry; excludes password and unnecessary personal data. No refresh token, revocation list, session store, roles, OAuth2, or permission matrix. (FR-3, FR-4; Section 14; AC-AUTH-4-7)
- Ownership checks protect Wallet and Transaction access. Transaction detail must constrain by participant in the query or enforce equivalent service-layer authorization; unrelated and unknown IDs share `404`. (FR-6, FR-11, FR-12; BR-T7; AC-TXN-4)
- Apply request validation and repeat critical rules in service/database layers. Use parameterized JPA/repository persistence; never construct SQL from untrusted input. (Section 15; Section 18)

### Transactions and concurrency

- Transaction boundaries surround complete registration and Transfer business operations rather than controllers or individual repository writes. (FR-2, FR-8; BR-U5, BR-T5; Sections 10.1-10.2)
- Concurrent duplicate registration is resolved authoritatively by the unique email constraint and maps to `409`. Competing Transfers cannot overspend or lose committed updates. (FR-1, FR-9; SM-3; Section 19; AC-AUTH-3, AC-TRANSFER-5)
- The negative-balance check is a database backstop, not a substitute for protected balance validation. Both Wallets are acquired deterministically to reduce opposite-direction deadlocks. (FR-9; Section 10.3; AC-WALLET-4)

### Error contract

- Centralize mapping to `{timestamp,status,error,message,path}` with optional safe `fieldErrors`; align Spring Security failures with this shape where practical. (Section 16)
- Binding mappings include validation `400`, generic credential/token `401`, duplicate email and concurrency conflict `409`, unknown/inaccessible resource `404`, self-transfer/insufficient funds `422`, and generic rolled-back transaction/database failure `500`. (Section 13; Section 16)
- Never expose stack traces, SQL, class names, internal exception messages, hashes, JWT contents, secrets, or full sensitive payloads. (Section 16; Section 18; AC-QUALITY-5)

### Tests

- Unit tests cover focused business/domain rules and service validation; integration tests cover HTTP, security, JPA, constraints, transaction rollback, and actual PostgreSQL behavior. (Section 19; AC-QUALITY-2)
- PostgreSQL-backed tests are mandatory where locking, rollback, and concurrency semantics matter; do not substitute H2/in-memory behavior. (Section 19; SM-2, SM-3)
- Required proof includes atomic registration, case-normalized duplicate rejection including a race, usable/invalid/expired JWT behavior, Wallet ownership, exact balance arithmetic, rollback after a forced mid-transfer failure, competing-transfer overspend prevention, participant-only history/detail, deterministic pagination, and failed-transfer absence. (Section 19; AC-AUTH-1-7, AC-WALLET-1-4, AC-TRANSFER-1-6, AC-TXN-1-5)
- OpenAPI must describe all seven endpoints, schemas, validations, representative errors, and JWT bearer security with no real secrets. (FR-14; AC-QUALITY-1)

## Explicitly out of scope

- Product capabilities: deposits, withdrawals, public funding, bank/payment-gateway integration, cards, KYC, OTP, email verification, password reset, loans, currency exchange, multi-currency, international transfers, refunds, schedules/recurrence, rewards, crypto, admin, notifications, fraud detection, complex reporting, and any web/mobile UI.
- Authentication additions: refresh tokens, logout/revocation, OAuth2, roles, permissions, session storage, or account states beyond existence.
- Architecture/infrastructure: microservices, API Gateway, service discovery, Kafka or other brokers, Redis/cache, events/domain-event infrastructure, CQRS, event sourcing, separate databases, sagas, distributed locks/transactions, eventual consistency, distributed tracing, Kubernetes, or complex DevOps/observability platforms.
- Domain expansion: Transfer, Ledger, BalanceHistory, Currency, Payment, Account, AuditLog, Notification, or Recipient as persisted entities; many-to-many relationships added for demonstration.
- Scope must not grow merely for future extraction. Auth/User/Wallet/Transaction service extraction, inter-service communication, Kafka, and distributed consistency are documentation-only future learning topics. (Section 4; Section 11; Section 22; SM-C1)

## Source tensions and architecture-owned gaps

No blocking contradiction was found. The following points require architecture decisions without altering the PRD:

1. **Concurrency mechanism is deliberately open.** The PRD requires PostgreSQL row-level protection and deterministic Wallet-ID ordering, but leaves the choice among JPA pessimistic locking, explicit SQL, or another correct PostgreSQL mechanism to architecture. The memlog explicitly supersedes a previously preselected pessimistic-lock implementation.
2. **Transfer orchestration ownership was refined.** The memlog supersedes Wallet-owned orchestration. Architecture should place cross-module coordination in a focused application/domain service while retaining Wallet balance behavior and Transaction persistence in their owning modules; it must not add a Transfer entity.
3. **JPA mapping details are open.** Owning sides are constrained by the schema (Wallet owns `user_id`; Transaction owns both Wallet FKs), but fetch types, carefully limited cascades, orphan-removal choices, and whether any inverse collection exists remain architecture decisions. They must preserve immutability, bounded queries, and DTO-only API serialization.
4. **Security/configuration values are open.** JWT signing algorithm/key format, token lifetime, password encoder parameters, migration-tool selection, and local seed strategy are implementation configuration choices. They must be documented, externalized where sensitive, and must not create new API behavior.
5. **No product-grade funding path exists by design.** Transfer demonstrations depend on non-production fixtures. Architecture must isolate any helper/seed mechanism from production profiles and routes; this is a deliberate MVP limitation, not a missing endpoint.

## Reconciliation conclusion

The architecture can proceed without changing the finalized PRD. Its principal task is to select concrete Spring/JPA/PostgreSQL mechanisms for the open implementation choices while preserving all FR, BR, AC, endpoint, data, security, atomicity, and scope constraints above.
