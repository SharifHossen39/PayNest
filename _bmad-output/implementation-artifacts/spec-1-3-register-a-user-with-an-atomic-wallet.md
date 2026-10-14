---
title: 'Story 1.3: Register a User with an Atomic Wallet'
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

**Problem:** PayNest can persist Users and Wallets but has no public registration use case. Valid signup must create exactly one zero-balance USD Wallet without exposing credentials or leaving partial data.

**Approach:** Add the public signup endpoint and a concrete RegistrationService that validates canonical input, hashes the untouched password, explicitly persists User and Wallet inside one local transaction, and returns only safe DTOs. PostgreSQL remains authoritative for duplicate-email races.

## Boundaries & Constraints

**Always:** Expose only `POST /api/auth/signup`; return `201` with `user {id,name,email,createdAt}` and `wallet {balance,currency,createdAt}`; canonicalize name/email before validation and lookup using existing Unicode/Locale.ROOT rules; measure limits in code points; preserve the 8–72-character password byte-for-byte and encode with `{pbkdf2@SpringSecurity_v5_8}`; explicitly save one User and one `0.00 USD` Wallet in one transaction; use safe `VALIDATION_ERROR`, `EMAIL_ALREADY_EXISTS`, and `INTERNAL_ERROR` responses; prove behavior with PostgreSQL.

**Never:** Return/log credentials, entity graphs, tokens, SQL/constraint details, stack traces, or security configuration; auto-authenticate; leave partial registration; add cascaded provisioning, migrations, entities, endpoints, JWT, roles, funding, abstraction-only services/interfaces, or H2.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Valid signup | Unique canonical email and valid fields | `201`; normalized safe User plus one `0.00 USD` Wallet | No token or credential data |
| Invalid fields | Bad name/email or password outside 8–72 code points | No persistence call | `400 VALIDATION_ERROR` with safe optional field errors |
| Password boundary | Exact 72-character multibyte password | Full unchanged value is hashable/matches; altered last character does not | Never echo the password |
| Existing email | Case/Unicode-edge-whitespace variant of stored email | No additional User or Wallet | `409 EMAIL_ALREADY_EXISTS` |
| Concurrent duplicate | Two independent requests for one normalized email | Exactly one User and one Wallet commit | One `201`; loser gets the same safe `409` |
| Wallet write failure | User insert succeeds inside the open registration transaction, Wallet insert fails | Both writes roll back | Safe `500 INTERNAL_ERROR` |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/auth/api/` — add AuthController, SignUpRequest/SignUpResponse, and focused boundary validation; no controller business logic.
- `src/main/java/com/paynest/auth/application/RegistrationService.java` — own normalization, advisory duplicate check, hashing, explicit persistence, result mapping, and the transaction boundary.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — provide the selected PBKDF2 encoder and the minimal stateless chain that permits signup while leaving JWT for later stories.
- `src/main/java/com/paynest/common/error/` — add ApiError, a small duplicate-email exception, and centralized 400/409/500 mappings.
- `src/main/java/com/paynest/user/` — reuse User/EmailNormalizer, add UserResponse and repository existence lookup, and expose only the smallest normalization access needed.
- `src/main/java/com/paynest/wallet/` — reuse explicit zero-USD creation/repository and add WalletResponse; add no cascade or provisioning layer.
- `src/test/java/com/paynest/auth/` — cover encoding plus HTTP validation, safety, rollback, and duplicate concurrency against PostgreSQL.

## Tasks & Acceptance

**Execution:**
- [x] Add canonical, code-point-aware signup validation and exact safe request/response DTO mapping.
- [x] Configure the v5.8 PBKDF2 DelegatingPasswordEncoder and minimal public-signup security policy.
- [x] Implement transactional registration with explicit flush points and constraint-specific duplicate translation.
- [x] Add the small centralized error slice required by this endpoint.
- [x] Add focused unit tests plus real-PostgreSQL HTTP, rollback, and concurrency integration tests.

**Acceptance Criteria:**
- Given valid unique input, when unauthenticated signup is called, then it returns the exact safe `201` contract and persists one normalized User with a v5.8 PBKDF2 hash and one zero-USD Wallet.
- Given any invalid request, when signup is called, then it returns a safe `400` and neither repository is invoked for persistence.
- Given sequential or concurrent equivalent emails, when signup is attempted, then one canonical User/Wallet pair exists and every loser receives the same safe `409`.
- Given a Wallet persistence failure after the User flush, when registration exits, then PostgreSQL contains neither record and the client receives a safe `500`.

## Implementation Notes

- Added the public signup HTTP boundary, canonical validation, safe DTOs/errors, PBKDF2 security configuration, and a concrete transactional registration coordinator with explicit User/Wallet flushes.
- Added unit coverage for validation and exact 72-code-point password encoding plus PostgreSQL/Testcontainers HTTP coverage for safe success/error contracts, sequential and concurrent duplicates, and rollback after a test-only Wallet insert failure.
- Reconciled the error body with the PRD's exact `timestamp/status/error/message/path/fieldErrors?` contract after acceptance audit, then verified all 22 tests on Java 25.0.4.1 against PostgreSQL 18.6.
- Applied independent-review fixes: robust canonical email validation, malformed Unicode/NUL rejection, POST-only signup authorization, deterministic named-constraint coverage, exact field boundaries, malformed/missing HTTP bodies, bounded concurrency waits, default-deny proof, and sensitive-log assertions.
- Suppressed Hibernate's constraint-detail JDBC warning logger so duplicate and forced database failures do not disclose SQL or constraint details in application logs.

## Spec Change Log

- 2026-10-12: Replaced normative container-test guidance with the guarded dedicated `paynest_test` strategy. Earlier container execution entries remain historical, non-authoritative evidence.

## Review Triage Log

| ID | Verdict | Evidence and route |
|---|---|---|
| E1 | medium | `isEmailAddress` accepts consecutive dots, invalid labels, and NUL; invalid client input can persist or become a 500. Patch with robust canonical-email validation and focused cases. |
| E2 | medium | A two-code-point name containing NUL passes application validation but PostgreSQL rejects it after persistence begins. Patch unrepresentable control input to a field validation error. |
| E3 | low | `stripUnicodeEdges(null)` throws NPE, but every production caller guards null and the method has no standalone nullable contract. Reject; an extra guard adds no current user protection. |
| E4 | false | Cyclic exception causes can be manufactured, but repository/Hibernate exception chains are framework-created and no reachable registration path creates a cycle. Reject as unreachable here. |
| E5 | medium | Concurrent-test `Future.get()` is unbounded, so a locking regression can hang verification. Patch with a bounded wait. |
| E6 | medium | Confirms E1: `a@b..com` currently reaches persistence despite the claim that invalid requests are rejected first. Patch with E1. |
| V1 | medium | The race test can win through the advisory precheck and does not deterministically execute named-constraint translation. Patch with a focused service test that forces `uk_users_email`. |
| V2 | medium | No HTTP test submits malformed JSON, so the unreadable-body 400 handler can regress unnoticed. Patch the PostgreSQL MockMvc suite. |
| V3 | medium | Omitted required fields are untested through HTTP, leaving ordinary incomplete payloads able to regress to 500. Patch parameterized missing-field cases. |
| V4 | medium | The five-character rejection does not pin the exact seven/eight-code-point boundary. Patch exact rejection and acceptance cases. |
| V5 | medium | Signup-public/default-deny security has no denied-route proof. Patch an integration assertion for a real non-signup route. |
| B1 | medium | Confirms E1 with additional malformed-label/control examples. Patch with E1. |
| B2 | medium | An unpaired surrogate is not a Unicode character and may fail inside PBKDF2 as a 500. Patch well-formed Unicode validation before encoding. |
| B3 | medium | The string matcher authorizes every method at the signup path. Patch it to match only HTTP POST. |
| B4 | low | The catch-all can map unrelated MVC protocol errors to 500, but this story defines only POST signup's 400/409/500 contract and choosing new 404/405/415 codes would add unspecified scope. Reject for this story. |
| B5 | low | Unexpected failures have no rich diagnostic after sensitive Hibernate logging is suppressed, but correlation/observability infrastructure is explicitly excluded and no diagnostic contract is required. Reject. |
| B6 | medium | Confirms V1: the concurrent start latch does not prove both prechecks passed. Patch with V1. |
| B7 | medium | Safe response bodies are proven, but the approved no-sensitive-logs boundary lacks test evidence. Patch focused captured-output assertions without adding infrastructure. |
| B8 | medium | Required name/email/password equality and isolated invalid boundaries are incompletely pinned. Patch concise parameterized service tests. |
| B9 | medium | Confirms E5: a blocked future can hang indefinitely. Patch with E5. |

## Design Notes

Use focused signup validation because raw `@Size` counts UTF-16 units and raw `@Email` precedes canonicalization; domain factories remain the second boundary. The normalized-email precheck is advisory: only the named `uk_users_email` violation maps to `EMAIL_ALREADY_EXISTS`. Flush inside RegistrationService so database failures occur within its transaction. Prove rollback with a test-only PostgreSQL Wallet-insert failure and race safety with independent transactions; add no production test hook.

## Verification

**Commands:**
- `mvn.cmd -q '-Djava.version=21' test` - passed locally: 22 tests, 0 failures/errors/skips, including all 5 PostgreSQL signup integration scenarios. Java 25 was not installed in the execution environment.
- `docker run ... maven:3.10.0-eclipse-temurin-25 ./mvnw test` - passed authoritatively: 22 tests, 0 failures/errors/skips on Java 25.0.4.1 with PostgreSQL 18.6.
- Post-review Java 25/PostgreSQL 18.6 full suite - passed: 37 tests, 0 failures/errors/skips.
- `./mvnw test` — expected: all unit and dedicated-PostgreSQL integration tests pass on Java 25; Flyway still validates only V1/V2 and Hibernate validates the unchanged schema.
