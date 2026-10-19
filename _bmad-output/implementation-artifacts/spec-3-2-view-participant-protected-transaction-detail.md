---
title: "Story 3.2: View Participant-Protected Transaction Detail"
type: feature
created: 2026-10-15
status: done
route: dispatch
review_loop_iteration: 0
baseline_commit: a73a7d255246a86540b54e5ef6e99e78f7d36ca1
context:
  - _bmad-output/implementation-artifacts/epic-3-context.md
---

# Story 3.2: View Participant-Protected Transaction Detail

## Frozen Decisions

### Intent and approach

Implement `GET /api/transactions/{id}` so an authenticated user can view one transaction only when their Wallet is its sender or receiver. Resolve the Wallet from the trusted JWT principal, then perform one ownership-constrained detail query using both the transaction ID and Wallet ID. Map the result to a dedicated response DTO with viewer-relative direction and safe participant identity fields.

### Invariants

- Always authorize detail access from the authenticated User ID and that User's current Wallet.
- Always query by transaction ID and participant Wallet ID together; unknown and unrelated IDs produce the same safe not-found response.
- Always return objective `sender` and `recipient` identity plus viewer-relative `direction`.
- Never accept caller-supplied user or Wallet identity for authorization.
- Never expose Wallet IDs, password data, entity graphs, persistence metadata, or JWT contents.
- Never load a transaction unrestricted and perform ownership checks afterward.
- Preserve the existing shared security contract for this story: missing or invalid JWT returns `401 AUTHENTICATION_FAILED`. The PRD-wide `INVALID_TOKEN` naming mismatch remains deferred to later security hardening, as decided in Story 3.1.

### Input/output matrix

| Input or condition | Observable result |
| --- | --- |
| Authenticated sender requests a participated transaction | `200`; `direction: SENT`; exact safe detail DTO |
| Authenticated receiver requests a participated transaction | `200`; `direction: RECEIVED`; exact safe detail DTO |
| ID is nonpositive, malformed, or outside supported BIGINT range | `400 VALIDATION_ERROR` with an `id` field error; no Wallet/detail query |
| ID is unknown or belongs to unrelated Wallets | Identical `404 TRANSACTION_NOT_FOUND` code and message |
| Authenticated User has no Wallet | `404 WALLET_NOT_FOUND` |
| JWT is missing or invalid | `401 AUTHENTICATION_FAILED` via existing security handling |

## Code Map

- `src/main/java/com/paynest/transaction/api/TransactionController.java` — add the detail route and validate/parse the path ID before service invocation.
- `src/main/java/com/paynest/transaction/api/TransactionDetailResponse.java` — define the exact public detail contract, including nested safe participant identities.
- `src/main/java/com/paynest/transaction/application/TransactionQueryService.java` — resolve the current Wallet, execute the protected query, derive direction, and map the response.
- `src/main/java/com/paynest/transaction/persistence/TransactionRepository.java` — add one ownership-constrained projection query.
- `src/main/java/com/paynest/transaction/persistence/TransactionDetailRow.java` — projection containing only fields required for safe mapping.
- `src/main/java/com/paynest/common/error/TransactionNotFoundException.java` — represent the indistinguishable inaccessible/unknown outcome.
- `src/main/java/com/paynest/common/error/InvalidTransactionIdException.java` — represent deterministic path-ID validation failures without a broad exception hierarchy.
- `src/main/java/com/paynest/common/error/GlobalExceptionHandler.java` — map the two focused errors to the stable API error contract.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — protect the single-segment transaction-detail route and route anonymous access through the JSON authentication entry point.
- `src/test/java/com/paynest/transaction/TransactionDetailHttpIntegrationTests.java` — PostgreSQL-backed endpoint, ownership, response-shape, query-boundary, and security tests.
- `src/test/java/com/paynest/wallet/CurrentWalletHttpIntegrationTests.java` — update the endpoint inventory from six to exactly seven.

## Implementation Tasks

- [x] Add a dedicated detail response and persistence projection with `id`, `direction`, `sender {name,email}`, `recipient {name,email}`, `amount`, `currency`, `status`, and `createdAt`.
- [x] Add the repository query constrained by `transactionId` and participant `walletId`, joining only the two participant Users needed by the projection.
- [x] Extend `TransactionQueryService` with the detail use case, reusing focused current-Wallet resolution and mapping an empty protected result to `TransactionNotFoundException`.
- [x] Add `GET /api/transactions/{id}`. Accept only a supported positive decimal BIGINT representation and reject invalid IDs before Wallet or transaction lookup.
- [x] Add small, safe error mappings for invalid ID and transaction not found; retain all existing error response fields and security behavior.
- [x] Update security matching so the detail endpoint is authenticated consistently with transaction history.
- [x] Add native PostgreSQL integration coverage and update the seven-endpoint inventory assertion.

## Acceptance Criteria

1. **Given** the authenticated User's Wallet sent the transaction, **when** they request its ID, **then** the API returns `200` with `direction: SENT` and the exact safe detail fields.
2. **Given** the authenticated User's Wallet received the transaction, **when** they request its ID, **then** the API returns `200` with `direction: RECEIVED`; `sender` and `recipient` retain their objective roles.
3. **Given** a valid positive ID for an unrelated transaction or an unknown transaction, **when** detail is requested, **then** both responses are indistinguishable `404 TRANSACTION_NOT_FOUND` responses and disclose no participation information.
4. **Given** a zero, negative, malformed, decimal, or BIGINT-overflow ID, **when** detail is requested, **then** the API returns `400 VALIDATION_ERROR` with `fieldErrors.id` before any Wallet/detail query. `Long.MAX_VALUE` remains supported and returns `404` when absent.
5. **Given** caller-supplied `userId` or `walletId` query parameters, **when** detail is requested, **then** they do not influence authorization or the response.
6. **Given** a successful request, **then** authorization and retrieval occur through one ownership-constrained detail query after the existing JWT User and Wallet resolution, without entity graph serialization or collection fetching.
7. **Given** no Wallet for the authenticated User, **then** the API returns `404 WALLET_NOT_FOUND` and does not query transaction detail.
8. **Given** a missing or invalid JWT, **then** the API returns the existing safe `401 AUTHENTICATION_FAILED` response without accessing protected data.
9. **Given** the completed implementation, **then** the application still exposes exactly the seven PRD endpoints and all existing tests remain green.

## Implementation Notes

- Use the selected native PostgreSQL integration-test setup; do not introduce H2, containers, migrations, entities, endpoints, or funding APIs.
- Prefer a small concrete implementation. No new service interfaces or repository abstraction layers are required.
- This story changes no database schema and performs no irreversible data operation.

## Verification

Run with process-scoped test database environment variables rather than storing credentials:

```powershell
mvn.cmd '-Dtest=TransactionDetailHttpIntegrationTests,CurrentWalletHttpIntegrationTests' test
mvn.cmd test
git diff --check
```

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Route and evidence |
| --- | --- | --- |
| Blind hunter: matrix-parameter suffix may be normalized before path-variable validation | low | Rejected. Spring may bind `/1;foo=bar` as ID `1`, but this unusual URI still resolves only the already-authorized transaction and a strict raw-URI guard would add disproportionate routing complexity. |
| Blind hunter: receiver response test does not assert the full response contract | low | Patch. The receiver path uses the same mapper, but exact receiver-side fields and leakage protections are not directly verified; adding assertions is a direct test correction. |
| Blind hunter: query-count test does not prove repository-level participant restriction | medium | Patch. Statement counts cannot distinguish the required constrained projection from an unrestricted projection followed by application authorization. |
| Blind hunter: authentication tests do not assert zero protected-data SQL or the complete safe envelope | low | Patch. Current security code prevents repository access, but direct query-boundary and envelope assertions are missing and inexpensive. |
| Blind hunter: not-found helper omits explicit body `status` and `fieldErrors` assertions | low | Patch. The five-field size indirectly constrains the envelope, but direct assertions better freeze the public contract. |
| Blind hunter: URI-normalization edge cases are not tested | low | Rejected with the matrix-parameter finding. Encoded delimiter behavior is container normalization outside ordinary client use, and adding raw-path policy guards is not justified for this MVP. |
| Blind hunter: verification section does not name database environment variables | low | Rejected because its proposed fix edits this build's spec; the authoritative variable names remain in `application-test.yml`. |
| Blind hunter: change-log and triage placeholders were empty at review start | false | Rejected. An empty change log is correct when no loopback has occurred, and the triage section is populated as part of this review step. |
| Verification-gap reviewer: participant constraint is not directly verified at the repository boundary | medium | Patch. Pre-verified gap: no existing test calls `findDetailByIdAndParticipant` with an unrelated Wallet and asserts an empty result. |
| Verification-gap reviewer: anonymous malformed detail paths are not covered | low | Patch. The matcher currently covers any single segment, but a direct regression test is needed so narrowing it to numeric-only cannot change `401` to `403`. |
