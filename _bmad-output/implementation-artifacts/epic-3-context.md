# Epic 3 Context: Private Transaction History and Detail

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Enable an authenticated User to review a bounded, deterministic history of completed money movements and inspect an individual Transaction only when their Wallet participated. The API must present the same immutable Transaction from the viewer's perspective while preventing identifier probing, unrelated-data disclosure, unbounded reads, and persistence-graph leakage.

## Stories

- Story 3.1: View Paginated Transaction History
- Story 3.2: View Participant-Protected Transaction Detail

## Requirements & Constraints

- `GET /api/transactions` and `GET /api/transactions/{id}` are JWT-protected. Identity and ownership come exclusively from the verified principal's User ID; clients never supply an owner User or Wallet ID.
- History contains only Transactions whose sender or receiver Wallet belongs to the authenticated User. Direction is calculated per viewer as `SENT` or `RECEIVED`; it is not persisted.
- History summaries expose only Transaction ID, viewer-relative direction, counterparty name and email, exact amount, `USD`, `COMPLETED`, and an ISO 8601 UTC creation timestamp. Detail may expose safe sender and recipient name/email data, but neither response may expose Wallet IDs, credentials, internal metadata, or entity graphs.
- Pagination is zero-based, defaults to `page=0` and `size=20`, accepts `page >= 0` and `1 <= size <= 100`, and returns `page`, `size`, `totalElements`, `totalPages`, and `last`. Ordering is always `createdAt DESC, id DESC`; no caller-selectable sort is required.
- Invalid pagination or non-positive/malformed Transaction IDs return `400 VALIDATION_ERROR` without falling back to an unrestricted query. Missing or invalid authentication returns `401 INVALID_TOKEN`.
- Unknown and unrelated Transaction IDs must be indistinguishable: both return the same safe `404 TRANSACTION_NOT_FOUND` response and disclose no participant or existence information. A missing current Wallet returns `404 WALLET_NOT_FOUND`.
- Only successfully committed Transfers appear. Transactions are immutable and completed-only; failed or rolled-back Transfers leave no history record.
- Reads must be bounded at the database. A 20-item page must not load the full history or trigger per-row counterparty queries, and the participant query must be supported by the planned indexes.
- Verify HTTP, security, JPA, Flyway, authorization, pagination, and query behavior against the dedicated developer-managed PostgreSQL database named `paynest_test`, configured through `PAYNEST_TEST_DB_URL`, `PAYNEST_TEST_DB_USERNAME`, and `PAYNEST_TEST_DB_PASSWORD`. H2 and container-based substitutes are outside scope; destructive test setup requires a database-name guard.

## Technical Decisions

- Keep the work inside the `transaction` feature: `TransactionController` delegates to `TransactionQueryService`, which uses `WalletRepository` to resolve the trusted User ID to a Wallet ID and `TransactionRepository` for participant-constrained reads. Controllers perform HTTP translation and validation only.
- Query history with `sender_wallet_id = :walletId OR receiver_wallet_id = :walletId`, a bounded pageable, and the fixed two-column descending sort. The schema supplies separate `(sender_wallet_id, created_at DESC, id DESC)` and `(receiver_wallet_id, created_at DESC, id DESC)` indexes; verify the query plan or equivalent SQL shape with representative data before considering any additional index.
- Query detail by Transaction ID and participant Wallet ID in one ownership-constrained operation. An empty result is the sole application outcome for both nonexistent and unauthorized records.
- Use dedicated response DTOs and explicit mapping. Transaction-to-Wallet associations remain required, lazy, unidirectional many-to-one relationships; Wallet gains no incoming or outgoing Transaction collections. Queries explicitly fetch or project only the participant data needed for mapping so serialization never traverses lazy entities and history avoids N+1 behavior.
- Preserve the existing data contract: `Long`/`BIGINT` identifiers, `BigDecimal`/`NUMERIC(19,2)` amounts, `Instant`/`TIMESTAMPTZ` timestamps, and the completed-only Transaction status. Flyway owns the append-only schema and Hibernate validates it.
- Keep the package-by-feature modular monolith and existing dependency direction. Do not add endpoints, entities, general sorting, caching, microservice seams, mapping frameworks, or speculative abstractions for these read use cases.

## Cross-Story Dependencies

- Both stories depend on Epic 1's stateless JWT principal flow, current User-to-Wallet ownership lookup, shared safe error envelope, and protected-endpoint policy.
- Both stories read the immutable completed Transactions and participant relationships established by Epic 2, including the `V3__create_transactions.sql` schema and history indexes. Epic 2's rollback guarantee ensures failed Transfers never appear.
- Story 3.2 should reuse the trusted Wallet resolution, direction calculation, explicit DTO mapping, and participant-query conventions established for Story 3.1 while preserving its stricter indistinguishable-`404` rule.
