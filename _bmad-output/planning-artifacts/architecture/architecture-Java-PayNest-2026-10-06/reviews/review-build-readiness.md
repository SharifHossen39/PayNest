# Adversarial Build-Readiness Re-review — PayNest Architecture

## Verdict

**PASS.** The roadmap update closes the sole remaining must-fix. Phase 9 is now explicitly an internal `TransferService` learning checkpoint with no public Transfer endpoint. Phase 10 adds the Transaction entity, completed Transaction insert, `TransferController`, and exact PRD response together, so no temporary or non-compliant public contract can exist.

No remaining must-fix was found.

## BRF-1 closure verification

**Prior finding:** Phase 9 introduced `TransferController` before the Transaction entity/insert existed, making the public endpoint incapable of returning the fixed PRD response or satisfying atomic Transfer semantics.

**Status: CLOSED.** The updated roadmap now states:

- Phase 9 is “Basic Send Money internals.”
- Phase 9 implements `TransferService` and amount, self-transfer, Recipient, and balance rules only.
- Phase 9 explicitly has **no public endpoint** and uses service-level non-concurrent tests.
- Phase 10 is “Transaction entity + expose Transfer API.”
- Phase 10 adds the sender/receiver Wallet mappings, completed Transaction insert, `TransferController`, and PRD response.
- The endpoint gate requires one record for success and none for rejection.
- Rollback proof, pessimistic locking, deterministic ordering, timeout behavior, and concurrency tests remain required before final MVP acceptance.

This sequencing now permits the learner to study balance orchestration first without exposing an invalid endpoint or weakening FR-8, FR-10, BR-T5, AC-TRANSFER-1, or AC-TXN-5.

## Implementation-unit re-check

### Registration

**PASS.** `RegistrationService` remains the single atomic boundary for normalized User creation, PBKDF2 hashing, User persistence, and exactly one zero-USD Wallet. Direct User/Wallet repository use is explicit; a Wallet-provisioning helper remains optional and is not a hidden requirement. Duplicate-email authority, rollback, and final tests remain binding.

### Basic-to-hardened Transfer

**PASS.** The progression is now internally coherent:

```text
Phase 9: internal TransferService balance/rule learning; no endpoint
  -> Phase 10: Transaction entity + completed insert + controller + PRD response
  -> Phase 12: forced rollback proof
  -> Phase 13: reproduce/reason about concurrency hazard
  -> Phase 14: ordered pessimistic locking
  -> Phase 15: PostgreSQL concurrency, opposite-direction, timeout, and constraint tests
  -> Phase 16: exact error-contract hardening
```

The final binding use case remains one `TransferService` transaction containing Sender/Recipient resolution, both ordered Wallet locks, under-lock validation, debit, credit, and completed Transaction insertion. `WalletTransferAccess` and `TransactionRecordingService` remain optional refactorings; their optionality does not make locking or recording optional.

### Transaction history and detail

**PASS.** `TransactionQueryService` may derive the current Wallet ID directly through the owning Wallet repository; a query helper is optional. Participant filtering, bounded pagination, fixed ordering, DTO projection, unrelated/unknown shared `404`, and the final N+1/query-shape test remain mandatory.

## Optional-helper audit

| Helper | Hidden requirement? | Required behavior remains owned by | Result |
|---|---|---|---|
| Wallet provisioning helper | No | `RegistrationService` + User/Wallet repositories | **PASS** |
| User lookup helper | No | Auth/Transfer coordinator + User repository | **PASS** |
| `WalletTransferAccess` | No | `TransferService` + ordered Wallet repository lock query | **PASS** |
| `TransactionRecordingService` | No | `TransferService` + Transaction repository | **PASS** |
| Wallet query helper | No | `TransactionQueryService` + Wallet repository | **PASS** |

No final correctness rule depends on an optional class name or interface.

## Final-correctness audit

| Requirement | Still required before final acceptance? | Result |
|---|---:|---|
| User + Wallet registration atomicity | Yes | **PASS** |
| Debit + credit + Transaction atomicity | Yes | **PASS** |
| Failed Transfer persists no Transaction | Yes | **PASS** |
| Pessimistic locks for both Wallets | Yes | **PASS** |
| Deterministic ascending lock order | Yes | **PASS** |
| No overspending, lost updates, or negative balances | Yes | **PASS** |
| Lock timeout, SQLSTATE mapping, no hidden retry | Yes | **PASS** |
| Forced post-debit rollback test | Yes | **PASS** |
| Ownership-protected history/detail | Yes | **PASS** |
| History query-count/N+1 verification | Yes | **PASS** |
| Exact PRD errors and OpenAPI contract | Yes | **PASS** |
| Docker/Compose final delivery | Yes, late phase | **PASS** |

The early/advanced labels control learning order only. They do not make final acceptance criteria optional.

## Scope check

- Exactly one Spring Boot modular monolith and one PostgreSQL database.
- Exactly three persisted entities: User, Wallet, Transaction.
- Exactly seven product endpoints, with Transfer exposed only once it is contract-complete.
- No public funding endpoint or production funding helper.
- No additional domain, entity, role, session, refresh token, notification, or fintech feature.
- No Kafka, Redis, gateway, discovery, separate database, saga/outbox, distributed transaction, or Kubernetes component.
- Future microservice material remains documentation/learning scope only.

## Remaining must-fixes

None.

## Gate recommendation

The refined architecture is ready for epics/stories and independent implementation. Preserve the distinction between internal learning checkpoints and contract-complete public features.
