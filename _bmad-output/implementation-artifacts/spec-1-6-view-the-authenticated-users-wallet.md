---
title: 'Story 1.6: View the Authenticated User''s Wallet'
type: 'feature'
created: '2026-10-07'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'NO_VCS'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** An authenticated PayNest User cannot yet retrieve the Wallet that registration created for them.

**Approach:** Add the exact protected `GET /api/wallet` endpoint, resolve ownership only from the verified JWT principal, and return the existing safe three-field `WalletResponse` through a small read-only Wallet query service.

## Boundaries & Constraints

**Always:** Require a valid Story 1.5 bearer identity; use only `AuthenticatedUser.id()` to find the current Wallet; return exactly `balance`, `currency`, and `createdAt`; preserve monetary scale, USD currency, stateless authentication, the shared safe `401 AUTHENTICATION_FAILED` contract, and deny-by-default behavior; perform no database writes. Treat a missing Wallet for an authenticated persisted User as a broken registration invariant and expose only the existing safe `500 INTERNAL_ERROR` envelope.

**Never:** Accept a caller-supplied User or Wallet ID as ownership evidence; expose Wallet/User IDs, entity relationships, password data, JWT data, or JPA entities; add a funding API, transfer behavior, migration, entity, dependency, role, session, or other endpoint; broadly authorize `/api/wallet/**` or weaken the fallback denial policy.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Current Wallet | Valid token for User A with A's Wallet | `200` with A's exact three-field Wallet DTO; no persistence mutation | N/A |
| Ownership manipulation | A's token plus query/header values naming User or Wallet B | Ignore untrusted identity noise and return only A's Wallet | N/A |
| Rejected bearer | Missing, malformed, invalid, expired, or stale token | No controller access and no authenticated session | Shared five-field `401 AUTHENTICATION_FAILED` plus `WWW-Authenticate: Bearer` |
| Broken invariant | Valid persisted User has no Wallet | No fabricated Wallet and no internal detail disclosure | Shared safe `500 INTERNAL_ERROR` |
| Route boundary | Wrong method, nested Wallet path, or other unlisted product route | Remain denied by the existing fallback | Existing deny-by-default behavior |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/wallet/api/WalletController.java` — add only the exact current-Wallet GET mapping and obtain the trusted principal with `@AuthenticationPrincipal`.
- `src/main/java/com/paynest/wallet/application/WalletQueryService.java` — add a concrete read-only use case that queries by authenticated User ID, maps to `WalletResponse`, and treats absence as an invariant failure.
- `src/main/java/com/paynest/wallet/api/WalletResponse.java` — reuse unchanged; it already exposes only `balance`, `currency`, and `createdAt`.
- `src/main/java/com/paynest/wallet/persistence/WalletRepository.java` — reuse unchanged `findByUserId(Long)`; do not add an abstraction.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — authenticate exact GET `/api/wallet` and select the existing JSON entry point for its anonymous requests while retaining exact Auth/docs rules and `denyAll` fallback.
- `src/main/java/com/paynest/auth/security/JwtAuthenticationFilter.java` and `JsonAuthenticationEntryPoint.java` — reuse unchanged inbound JWT and safe `401` behavior.
- `src/test/java/com/paynest/wallet/CurrentWalletHttpIntegrationTests.java` — add PostgreSQL-backed HTTP coverage for response shape, ownership, no writes, auth failures, invariant failure, route boundaries, and unchanged persistence model.

## Tasks & Acceptance

**Execution:**
- [x] `wallet/api/WalletController.java`, `wallet/application/WalletQueryService.java` — implement the ID-only current-Wallet query using the existing repository and DTO.
- [x] `auth/security/SecurityConfig.java` — narrowly protect exact GET `/api/wallet` with the established bearer-error contract.
- [x] `wallet/CurrentWalletHttpIntegrationTests.java` — cover every matrix row against Testcontainers PostgreSQL and assert safe, exact response envelopes and no writes.

**Acceptance Criteria:**
- Given valid tokens for Users A and B, when each requests `GET /api/wallet`, then each receives only their own exact three-field Wallet response and neither request changes persisted User or Wallet data.
- Given the completed story, when migrations, entities, and routes are inspected, then Flyway remains at V2, only User and Wallet are persisted, exactly four Epic 1 endpoints exist, and no Wallet funding or additional product endpoint exists.

## Implementation Notes

- Added the exact current-Wallet controller and a concrete read-only query service that maps the Wallet owned by the verified User ID to the existing `WalletResponse`.
- Extended security only for exact GET `/api/wallet`; anonymous and rejected bearer requests use the established JSON `401`, while wrong methods, nested paths, and unlisted routes remain forbidden.
- Added six PostgreSQL-backed HTTP tests covering every matrix row, exact monetary serialization, ownership isolation, absence of writes, safe invariant failure, Flyway V2, the two-entity metamodel, and the four-endpoint Epic 1 surface.
- Final Java 25.0.4.1/PostgreSQL 18.6 verification passed: 81 tests, 0 failures, errors, or skips.
- Review hardening reuses the exact Wallet matcher for authorization, exercises a funded `137.42` balance, pins anonymous and matrix-parameter route boundaries, and makes the endpoint inventory reject duplicate or unbounded mappings.
- Post-review Java 25.0.4.1/PostgreSQL 18.6 verification passed unchanged: 81 tests, 0 failures, errors, or skips; no review item was deferred.

## Spec Change Log

## Review Triage Log

| ID | Reviewer finding | Classification | Disposition |
| --- | --- | --- | --- |
| B1 | Wallet authorization and JSON-entry-point selection use matchers with different matrix-parameter semantics. | medium | Patch: reuse the exact request matcher for authorization and pin matrix-parameter variants as denied. |
| B2 | Missing-Wallet invariant failure emits no operator log. | low | Rejected: the approved contract requires a safe client failure but explicitly avoids observability infrastructure; adding logging behavior and log tests is disproportionate for this learning MVP. |
| B3 | The Wallet HTTP tests use only zero balances. | medium | Patch with V1: exercise a distinctive non-zero two-decimal balance through the endpoint and verify no persistence mutation. |
| B4 | Endpoint inventory silently ignores mappings without explicit HTTP methods. | medium | Patch with E4: make unbounded PayNest API mappings fail the inventory assertion. |
| B5 | Wallet HTTP tests do not repeat every malformed-bearer branch. | false | Rejected: this story covers each matrix category, while Story 1.5 already pins empty, duplicate, whitespace, missing-claim, and other shared filter branches; the Wallet route uses that unchanged filter. |
| B6 | Ownership noise is not tested in a GET request body. | false | Rejected: `WalletController` declares no body parameter and has no code path capable of consuming a body identity; query/header noise already proves lookup comes only from the principal. |
| E1 | Matrix-parameter Wallet requests can receive inconsistent route/authentication handling. | medium | Patch with B1: use one exact matcher and add route-boundary coverage. |
| E2 | The stale-token test ID can overflow near `Long.MAX_VALUE`. | false | Rejected: test IDs come from a fresh identity table and cannot approach that value in this suite. |
| E3 | Set-based endpoint inventory can hide a second conditional mapping with the same method and path. | medium | Patch: retain mapping multiplicity and assert exactly four PayNest API handler mappings. |
| E4 | An API mapping with no declared HTTP method is omitted by endpoint inventory. | medium | Patch with B4: explicitly reject empty method conditions. |
| V1 | A funded Wallet could regress to a hard-coded zero response without current HTTP tests failing. | medium | Patch with B3: persist a non-zero balance and assert its exact JSON value through `GET /api/wallet`. |
| V2 | Wrong-method/nested-path tests authenticate every request and do not exercise anonymous fallback selection. | medium | Patch: add anonymous requests that must remain `403`, not Wallet-route `401`. |

## Design Notes

The missing-Wallet path is an internal consistency failure, not a normal resource lookup: registration atomically establishes one Wallet per User and clients cannot request arbitrary Wallets. Reusing the generic `500` envelope avoids inventing a public not-found contract for a state the product says cannot exist.

## Verification

**Commands:**
- `./mvnw test` — expected: full Java 25 suite passes against PostgreSQL 18.6; current-Wallet ownership/security tests pass; Flyway remains V1/V2.
