---
title: 'Story 4.1: Finalize Safe Errors, Logging, and Configuration'
type: 'feature'
created: '2026-10-19'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '609dcd61688da1ef654a15d64ca8496e7eb8db6a'
context:
  - _bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest already handles most failures safely, but its final contract is inconsistent: bearer failures use the sign-in code, framework denials can be bodyless, one undocumented error code remains, safe operational logging is absent, and test JWT configuration plus environment-variable documentation are incomplete.

**Approach:** Centralize creation and servlet writing of the existing `ApiError`, align every MVC and security failure with the finalized vocabulary, add fixed-category non-sensitive startup/authentication/Transfer/failure logs, and complete environment-based configuration documentation without changing product behavior.

## Boundaries & Constraints

**Always:** Preserve `{timestamp,status,error,message,path}` plus optional non-empty `fieldErrors`; use only the thirteen approved error codes and PRD statuses; distinguish sign-in failure (`AUTHENTICATION_FAILED`) from missing/invalid bearer authentication (`INVALID_TOKEN`); return JSON `ACCESS_DENIED` for framework authorization denial; keep `WWW-Authenticate: Bearer` on 401 only; log useful fixed event/outcome categories without credentials, identities, money values, request bodies, raw exception details, SQL, tokens, hashes, or secrets; keep all runtime secrets externally configurable.

**Never:** Add endpoints, entities, migrations, roles, correlation IDs, tracing, metrics/observability platforms, logging dependencies, retry behavior, Docker/container tooling, fixture migrations, or OpenAPI/runbook work reserved for Stories 4.2 and 4.3. Do not change transaction atomicity, locking, rollback, authentication cryptography, or the unknown-versus-unrelated transaction contract.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Invalid sign-in | Unknown email or wrong password | Same generic response and safe failure log | `401 AUTHENTICATION_FAILED` |
| Invalid bearer | Missing, malformed, tampered, expired, or deleted-subject JWT on a protected route | Same five-field JSON envelope; no token data; Bearer challenge | `401 INVALID_TOKEN` |
| Framework denial | Disallowed method or unlisted route | Five-field JSON envelope; no Bearer challenge | `403 ACCESS_DENIED` |
| Invalid representation | Bad JSON, parameter type, unsupported content type, or rejected request syntax | Safe validation response without parser/firewall internals | `400 VALIDATION_ERROR` |
| Transfer rejection/conflict/failure | Business rejection, SQLSTATE `40P01`/`55P03`, or unexpected persistence failure | Existing rollback and status semantics; fixed safe outcome log | `422` business code, `409 CONFLICT`, or `500 TRANSACTION_FAILED` |
| Unexpected failure | Unhandled non-Transfer exception | Generic response and safe diagnostic category | `500 INTERNAL_ERROR` |
| Runtime configuration | Local/test environment overrides supplied | Database, JWT, TTL, and lock settings bind externally; sample contains placeholders only | Startup fails safely for invalid values |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/common/error/ApiError.java` and new `ApiErrorResponseWriter.java` — reuse the DTO and provide one clock-backed response creator/writer for advice and security filters.
- `src/main/java/com/paynest/common/error/GlobalExceptionHandler.java` — use the shared writer, remove `UNSUPPORTED_MEDIA_TYPE`, preserve known mappings, and emit fixed safe failure categories.
- `src/main/java/com/paynest/auth/security/JsonAuthenticationEntryPoint.java`, new `JsonAccessDeniedHandler.java`, new `JsonRequestRejectedHandler.java`, and `SecurityConfig.java` — render `INVALID_TOKEN`, `ACCESS_DENIED`, and rejected syntax consistently without weakening public auth/docs paths.
- `src/main/java/com/paynest/PayNestApplication.java`, `auth/api/AuthController.java`, and `transfer/api/TransferController.java` — add startup and post-success logs after completed service operations.
- `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, and `.env.example` — align the safe local username default, externalize test JWT secret/TTL with test-only defaults, and document all nine environment variables without real credentials.
- `src/test/java/com/paynest/common/error/GlobalExceptionHandlerTests.java` plus focused security/config tests — freeze mappings, response shape, headers, redaction, and environment-variable coverage.
- Existing auth, user, wallet, transfer, rollback, and transaction HTTP integration tests — update bearer-code assertions and verify representative logs/non-disclosure without duplicating every contract case.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/com/paynest/common/error/ApiErrorResponseWriter.java` and `GlobalExceptionHandler.java` — centralize safe response creation/writing, approved mappings, and fixed failure logs.
- [x] `src/main/java/com/paynest/auth/security/JsonAuthenticationEntryPoint.java`, `JsonAccessDeniedHandler.java`, `JsonRequestRejectedHandler.java`, and `SecurityConfig.java` — wire consistent JSON security and rejected-request failures into the stateless chain.
- [x] `src/main/java/com/paynest/PayNestApplication.java`, `auth/api/AuthController.java`, and `transfer/api/TransferController.java` — add fixed startup and successful-outcome logging only after the relevant operation succeeds.
- [x] `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, and `.env.example` — complete externalization and non-secret sample configuration.
- [x] `src/test/java/com/paynest/common/error/GlobalExceptionHandlerTests.java` and focused security/configuration tests — verify code/status mappings, headers, response shape, redaction, and all environment names.
- [x] Existing auth, user, wallet, transfer, rollback, and transaction HTTP integration tests — update protected-route expectations and verify representative logs plus PostgreSQL-backed regression behavior.

**Acceptance Criteria:**
- Given all representative controller and security failures, when responses are inspected, then every payload uses the shared safe shape, an approved code/status pair, and contains none of the forbidden details.
- Given startup, sign-in, Transfer success/rejection/conflict/failure, or unexpected failure, when captured logs are inspected, then the expected fixed category is present and sensitive sentinel values are absent.
- Given local/test overrides and the committed sample, when configuration tests run, then all nine variables are supported and no real secret is committed.
- Given the completed hardening, when endpoint/entity inventories and the full suite run, then exactly seven product endpoints and three entities remain and all prior behavior passes.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| Finding | Verdict / route | Evidence |
|---|---|---|
| Verification: transfer rejection logs are unverified | medium / patch | The business-rejection HTTP test exercises all four branches but never inspects their new log categories, so a category regression would pass. |
| Verification: access-denied logging is unverified | medium / patch | The focused handler test verifies only the response; removing the fixed denial log or logging the supplied sensitive exception would not fail it. |
| Verification: request-failure categories lack positive assertions | low / patch | Validation, not-found, duplicate-email, and non-Transfer persistence categories are newly emitted but their existing tests only verify responses or negative redaction. Direct assertions are small and prevent silent drift. |
| Edge case: malformed bearer on a denied route returns 401 | high / patch | `JwtAuthenticationFilter` currently validates bearer headers on every non-public route before authorization, so malformed credentials preempt the required `403 ACCESS_DENIED` for unlisted routes and disallowed methods. |
| Edge claim: changing unsupported media from 415 to 400 breaks preserved behavior | false / reject | The approved frozen matrix explicitly requires unsupported content types to return `400 VALIDATION_ERROR`; the change is intentional and required. |
| Blind: rejected URI path parameters can be reflected | high / patch | `ApiErrorResponseWriter` copies `request.getRequestURI()` and the focused rejected-request test proves a `;jsessionid=private-value` value is returned in the body. |
| Blind: invalid bearer preempts denied-route authorization | high / patch | This independently identifies the same verified filter-order outcome as the edge-case finding; denied routes are not excluded by `shouldNotFilter`. |
| Blind: repository failure during JWT subject lookup bypasses safe handlers | high / patch | `existsById` executes in the custom filter before downstream exception translation; an escaping data-access failure is not handled by MVC advice or any JSON security handler. |
| Blind: incompatible Accept can produce a bodyless/default failure | high / patch | A live Java 25 request with `Accept: application/xml` caused error serialization to throw `HttpMediaTypeNotAcceptableException`, forwarded to `/error`, returned the wrong `403`, and logged a framework stack trace. |
| Blind: invalid transfer validation is logged as a generic request | medium / patch | `handleValidation` always calls `logRequestRejected`, so an invalid transfer does not emit a Transfer outcome even though the request path is known. |
| Blind: failed startup lacks an application failure log | false / reject | The execution task requires the fixed startup log only after successful startup; the failure test correctly protects against a false `started` outcome while Spring reports bootstrap failure. |
| Blind: JWT secret override breaks sign-in integration verification | medium / patch | `application-test.yml` supports `PAYNEST_JWT_SECRET`, but `SignInHttpIntegrationTests` parses the issued token with a hard-coded default key. |
| Blind: JWT TTL override breaks the fixed 3,600-second assertion | medium / patch | The test profile supports `PAYNEST_JWT_TTL`, but the sign-in integration test still assumes a one-hour lifetime. |
| Blind: the shared writer should enforce codes with a typed definition | false / reject | The approved design requires centralized safe rendering and stable tested mappings, not a new error-type abstraction; all call sites use the frozen vocabulary and the proposed type would add unrequired complexity. |
| Blind: sign-in 401 should include a Bearer challenge | false / reject | The frozen matrix calls for the Bearer challenge on invalid bearer authentication; credential-based sign-in failure is a distinct `AUTHENTICATION_FAILED` flow and is not a bearer challenge. |
| Blind: invalid database startup is not covered safely | false / reject | Existing `JwtPropertiesTests` already verifies invalid external security values fail without leaking the sentinel, while runtime configuration tests cover all nine names; the story does not require destructive connection attempts for every invalid setting. |
| Blind: the specification contains mojibake | false / reject | The file contains valid UTF-8 em-dash bytes and `rg` reads them correctly; the apparent `â€”` text came from the review process's console decoding. |

## Design Notes

Keep log records deliberately small, for example `event=authentication outcome=success` or `event=transfer outcome=conflict`. Stable codes and outcomes are diagnostic context; caller data and throwable content are not.

## Verification

**Commands:**
- `mvn.cmd '-Dtest=GlobalExceptionHandlerTests,*Security*Tests,*Configuration*Tests' test` with required test database variables set — focused contracts pass.
- `mvn.cmd test` with `PAYNEST_TEST_DB_*` set for the guarded `paynest_test` database — full suite passes.
- `git diff --check` — no whitespace errors.
