# Epic 2 Context: Safe Money Transfer

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Enable an authenticated User whose Wallet has been funded through test/local fixtures to send an exact USD amount to another registered PayNest User. A successful Transfer must debit the Sender, credit the Recipient, and persist exactly one immutable `COMPLETED` Transaction in one local PostgreSQL transaction. Rejected, failed, or competing requests must leave balances and Transaction records consistent, with no negative balance, lost update, partial state, or durable failed Transaction.

## Stories

- Story 2.1: Persist Immutable Completed Transaction Records
- Story 2.2: Implement and Validate the Atomic Transfer Use Case
- Story 2.3: Prove Transfer Rollback and Safe Failure Behavior
- Story 2.4: Expose and Protect Concurrent Wallet Transfers

## Requirements & Constraints

- Keep the MVP as one modular Spring Boot application and one PostgreSQL database. Persist only `User`, `Wallet`, and `Transaction`; Transfer is an application use case with no entity, table, or repository.
- Each successful Transfer creates exactly one immutable Transaction containing a generated `Long` ID, required Sender and Receiver Wallets, exact positive `BigDecimal` amount, status `COMPLETED`, and UTC creation time. Failed Transfers persist no Transaction, and no other Transaction status is introduced.
- The Sender is derived exclusively from the trusted authenticated User ID. The Recipient is resolved by normalized email. Reject unknown recipients, self-Transfers, invalid monetary amounts, missing Wallets, and insufficient funds without changing state.
- Transfer amount is required, positive, has at most two fractional digits, and must fit PostgreSQL `NUMERIC(19,2)` without rounding. Both Wallets must use USD, and balances may never become negative.
- Sender debit, Recipient credit, and Transaction insertion form one local ACID operation. Every pre-commit failure rolls back all mutations; exceptions that should cause rollback must not be caught and suppressed.
- The final Transfer implementation must prevent lost updates and concurrent overspending with PostgreSQL row-level protection. Both Wallets are locked in deterministic ascending Wallet-ID order, state-dependent rules are rechecked under lock, and locks remain held through commit or rollback.
- `POST /api/wallet/transfers` is the only public API added by this epic and is exposed only after ordered locking is active. It requires a valid JWT, returns `201 Created` for success, and never accepts caller-supplied Sender or owner identifiers.
- Local demonstrations and automated tests may fund Wallets only through SQL fixtures, test-source helpers, direct test JDBC setup, or a manual local-only script. No production migration, application service, or REST funding endpoint may be added.
- Known validation and business failures use the stable safe error contract. Lock conflicts map to `409 CONFLICT` only after rollback; unexpected Transfer persistence failures map to `500 TRANSACTION_FAILED`. Responses and logs must not expose tokens, secrets, credentials, SQL, driver details, entity graphs, or stack traces.
- PostgreSQL-backed tests use the dedicated developer-managed `paynest_test` database configured through `PAYNEST_TEST_DB_*`. Verify the exact database name before cleanup or schema reset; do not use H2 or container tooling as evidence for constraints, rollback, or locking.

## Technical Decisions

- Add `V3__create_transactions.sql` without modifying the shared `V1` or `V2` migrations. It creates only `transactions`, named foreign-key and check constraints, and the two history indexes; Flyway owns schema evolution and Hibernate remains on `ddl-auto=validate`.
- Map `Transaction.senderWallet` and `Transaction.receiverWallet` as required lazy unidirectional `@ManyToOne` associations owned by Transaction. Wallet exposes no incoming or outgoing Transaction collections, and the mappings use no cascade delete or orphan removal.
- The `transactions` table uses generated `BIGINT` identity ID, required `sender_wallet_id` and `receiver_wallet_id` foreign keys, `NUMERIC(19,2)` amount, `VARCHAR(20)` status, and `TIMESTAMPTZ` creation time. Named checks enforce positive amount, distinct Wallets, and completed-only status.
- Create `idx_transactions_sender_history (sender_wallet_id, created_at DESC, id DESC)` and `idx_transactions_receiver_history (receiver_wallet_id, created_at DESC, id DESC)` with `V3` so Epic 3 can query bounded participant history without rewriting the migration.
- Provide one supported Transaction creation path that always selects `COMPLETED`, takes time from the injectable `Clock`, and provides no public mutation path for participants, amount, status, or creation time. Expose persistence through `TransactionRepository`; add no update or delete operation.
- Keep `TransferService` as the concrete application coordinator and transaction boundary. It may directly use `UserRepository`, `WalletRepository`, and `TransactionRepository`; `WalletTransferAccess`, `TransactionRecordingService`, repository wrappers, and interfaces are optional only when a concrete need improves readability.
- Wallet owns balance behavior such as debit, credit, and non-negative invariants. Controllers perform HTTP translation and boundary validation only; repositories provide focused persistence access and never orchestrate the Transfer.
- Build the use case progressively: first the non-concurrent service-level Transfer, then the transaction boundary and rollback proof, then the concurrency problem, pessimistic locking, deterministic ordering, and the public endpoint. The unlocked checkpoint is not the completed MVP.
- The final concurrency strategy is a focused `WalletRepository` query using JPA `PESSIMISTIC_WRITE` under PostgreSQL `READ COMMITTED`, requesting both rows in ascending ID order. Identify Sender and Recipient by ID rather than result position and revalidate available balance after locks are acquired.
- Configure a finite externalized `PAYNEST_DB_LOCK_TIMEOUT`, with a five-second local/test default and no automatic retry. PostgreSQL SQLSTATE `40P01` and `55P03` are the recognized deadlock/lock-timeout conflict cases; no JVM lock, distributed lock, broker, saga, or distributed transaction mechanism is permitted.
- Use a dedicated Transfer request DTO and response DTO. A successful result contains Transaction ID, viewer-relative direction `SENT`, safe Recipient name and email, exact amount, `USD`, `COMPLETED`, creation timestamp, and Sender `balanceAfter`, without exposing internal Wallet IDs or persistence relationships.

## Cross-Story Dependencies

Epic 1 supplies authenticated User identity, normalized recipient lookup, the one-User/one-Wallet invariant, exact Wallet balances, Flyway through `V2`, and protected-request handling. Story 2.1 adds the immutable Transaction mapping, repository, `V3` schema, constraints, and history indexes required by every later Epic 2 story. Story 2.2 builds the non-concurrent atomic Transfer behind `TransferService` but deliberately exposes no public endpoint. Story 2.3 proves that business, forced, and persistence failures roll back both balance mutations and Transaction insertion. Story 2.4 adds ordered pessimistic row locking, bounded conflict behavior, concurrency tests, and finally the protected Transfer endpoint. Epic 3 depends on the completed records and indexes created here for participant-only history and detail.
