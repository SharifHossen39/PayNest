---
title: 'Story 1.4: Sign In and Receive a JWT'
type: 'feature'
created: '2026-10-07'
status: 'done'
route: 'dispatch'
review_loop_iteration: 1
baseline_commit: 'NO_VCS'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Registered Users can create accounts but cannot exchange their credentials for the bearer token required by later protected journeys.

**Approach:** Add public sign-in through a concrete AuthenticationService that canonicalizes email, verifies the untouched password, and delegates minimal HS256 issuance to a JWT component with startup-validated external configuration.

## Boundaries & Constraints

**Always:** Expose only `POST /api/auth/signin`; normalize and syntax-check email before lookup while passing the password unchanged to `PasswordEncoder.matches`; return exactly `accessToken`, `tokenType: "Bearer"`, and `expiresAt`; sign HS256 tokens with decimal User ID `sub`, `iat`, and `exp` only; use the same second-precision `exp` in the response; require a Base64 secret of at least 32 decoded bytes and positive `PAYNEST_JWT_TTL` (default `60m`); use the shared errors; make unknown email and wrong password the same generic `401 AUTHENTICATION_FAILED`; preserve stateless deny-by-default security.

**Never:** Transform passwords; include email, password data, roles, Wallet ID, balance, or personal claims; return/log tokens, credentials, hashes, secrets, JWT contents, SQL, stack traces, or account-existence clues; add token filtering, refresh/logout/revocation, sessions, roles, OAuth2, persistence, migrations, entities, or other endpoints.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Valid credentials | Canonical email match and exact password | `200`; signed token, `Bearer`, matching expiry; no database write | N/A |
| Canonical email variant | Case/Unicode-edge-whitespace variation | Same User is found and authenticated | N/A |
| Invalid request | Missing/empty field, malformed email, malformed JSON | No credential lookup/token issuance | `400 VALIDATION_ERROR` |
| Invalid credentials | Unknown valid email or wrong password | Same response shape/message; no token | `401 AUTHENTICATION_FAILED` |
| Exact password | Leading/trailing spaces or 72-code-point multibyte value | Passed unchanged; only the exact stored password matches | Generic `401` on mismatch |
| Invalid JWT config | Missing/malformed/short secret or non-positive TTL | Application context fails clearly before serving requests | No secret material in failure/log output |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/auth/api/` — extend AuthController and add SignInRequest/TokenResponse with the exact three-field response.
- `src/main/java/com/paynest/auth/application/AuthenticationService.java` — canonical validation, User lookup, password verification, generic failure, and token-result orchestration; read only through UserRepository.
- `src/main/java/com/paynest/auth/security/` — add validated JWT settings/JwtService and permit only POST signup/signin; no Story 1.5 bearer filter.
- `src/main/java/com/paynest/common/error/` — add only the small sign-in validation/authentication exceptions and their safe 400/401 mappings to existing ApiError handling.
- `src/main/java/com/paynest/user/` — reuse UserRepository and the same canonical email rule as registration.
- `src/main/resources/application.yml` and `src/test/resources/application-test.yml` — bind external secret/Duration settings; keep the obvious fixed test key only in the explicit test profile.
- `src/test/java/com/paynest/auth/` — add focused configuration/JWT/service tests and PostgreSQL MockMvc sign-in contract/security/safety coverage.

## Tasks & Acceptance

**Execution:**
- [x] `auth/api/*`, `common/error/*` — add the sign-in DTO/HTTP boundary and safe 400/401 mappings.
- [x] `auth/security/*`, configuration files — add validated JWT settings and deterministic HS256 issuance.
- [x] `auth/application/AuthenticationService.java` — implement canonical lookup and unchanged password verification.
- [x] `auth/security/SecurityConfig.java` — permit POST sign-in without token consumption or weaker defaults.
- [x] `src/test/java/com/paynest/auth/*` — cover every matrix row with focused and PostgreSQL-backed tests.

**Acceptance Criteria:**
- Given valid credentials, when sign-in succeeds, then the exact `200` response contains a verifiable HS256 token whose minimal claims and configured lifetime match `expiresAt`, with no database mutation.
- Given malformed input, when sign-in is requested, then a safe `400` is returned before User lookup or token issuance.
- Given an unknown email or wrong exact password, when sign-in fails, then both paths return the same generic safe `401` contract and neither response nor captured logs reveal credentials, account existence, token data, or secrets.
- Given JWT configuration boundaries, when application configuration is validated, then a 32-byte Base64 key and positive default/override TTL succeed while missing, malformed, short, zero, or negative values fail clearly.

## Implementation Notes

- Added the exact three-field sign-in response and shared safe validation/authentication error handling.
- Added constructor-bound JWT settings that reject missing, malformed, short, zero, and negative configuration before startup completes.
- Issuance uses one second-precision instant for `iat`, `exp`, and `expiresAt`, with explicit HS256 signing and only `sub`, `iat`, and `exp` claims.
- Authentication is read-only, normalizes email before lookup, and passes the supplied password unchanged to `PasswordEncoder.matches`.
- Verified the complete Story 1.4 tree on Java 25.0.4.1 against PostgreSQL 18.6: all 53 tests passed and Flyway remained at V2.
- Review hardening now enforces whole-second JWT lifetimes of at least one second, returns a safe `415` for unsupported sign-in media types, and verifies production configuration wiring, log redaction, and exact route allowlisting.
- Final Java 25.0.4.1/PostgreSQL 18.6 verification passed: 59 tests, 0 failures, errors, or skips; Flyway remained at V2.

## Spec Change Log

- 2026-10-07: Implemented Story 1.4 and added focused plus PostgreSQL-backed acceptance coverage.
- 2026-10-07: Completed the three-layer review, applied accepted hardening fixes, and marked Story 1.4 done after full verification.

## Review Triage Log

| ID | Reviewer finding | Classification | Disposition |
| --- | --- | --- | --- |
| B1 | Fractional JWT TTL can make response `expiresAt` differ from the JWT NumericDate. | medium | Patch: require TTLs that are representable as whole positive seconds. |
| B2 | A positive sub-second TTL can serialize with `exp == iat`. | medium | Patch with B1: require at least one whole second. |
| B3 | An extremely large positive TTL can overflow `Instant` arithmetic during sign-in. | low | Rejected for this story: no product lifetime maximum is defined, and inventing an arbitrary policy for pathological configuration would exceed the approved contract. |
| B4 | Unknown-email authentication skips PBKDF2 and may have a timing difference. | false | Rejected: the approved Design Notes explicitly state that a dummy-hash lookup is not required; the observable HTTP and logging contracts remain identical. |
| B5 | Successful token responses do not add explicit no-cache headers. | low | Rejected: POST responses have no explicit cache freshness and the approved API contract does not require cache headers. |
| B6 | Unsupported sign-in media types fall through the catch-all and return `500`. | medium | Patch: add a narrow safe `415` mapping and focused HTTP coverage. |
| B7 | The generic exception handler does not add sanitized server-side logging. | low | Rejected: observability infrastructure and logging policy are outside Story 1.4; the handler already prevents internal details from reaching clients. |
| B8 | Security-chain rejections use Spring Security's default body instead of `ApiError`. | false | Rejected: bearer-consumption/security-entry-point work belongs to Story 1.5 and this story explicitly forbids pulling token filtering forward. |
| B9 | Sign-in has no maximum request-password length. | false | Rejected: the frozen design intentionally validates sign-in passwords as non-null/non-empty only and passes the exact credential unchanged. |
| B10 | Startup-failure tests do not prove malformed JWT secret material stays out of diagnostics. | medium | Patch: add negative assertions for exception text and captured startup output. |
| E1 | Unknown-email authentication has a different password-hashing path. | false | Rejected as the same issue as B4 and contrary to the approved no-dummy-hash decision. |
| E2 | Oversized passwords reach lookup and password verification. | false | Rejected as the same issue as B9; size is not malformed under the approved sign-in contract. |
| E3 | Very large positive JWT TTL values may overflow during issuance. | low | Rejected as the same issue as B3; the PRD defines no maximum TTL policy. |
| E4 | Unsupported sign-in media types are mislabeled as internal failures. | medium | Patch with B6 using a focused `415` handler and test. |
| V1 | Production `PAYNEST_JWT_SECRET`/`PAYNEST_JWT_TTL` placeholder wiring is not exercised. | medium | Patch: load production-style configuration with those environment names and assert binding. |
| V2 | Successful token issuance has no captured-output assertion against token or credential leakage. | medium | Patch: extend the successful sign-in test with negative log assertions. |
| V3 | Invalid JWT configuration tests do not assert that the supplied secret stays out of failures/logs. | medium | Patch with B10 using sensitive-input negative assertions. |
| V4 | Exact public-route allowlisting lacks an unlisted `POST /api/auth/*` assertion. | medium | Patch: add a denied unlisted POST assertion. |

## Design Notes

Use Spring `Duration` syntax (`60m` default), omit optional `iss`, truncate `iat` to seconds, and calculate `exp` once for JWT and response. Require the secret normally; `application-test.yml` supplies an obvious non-production 32-byte fixture, not fallback logic. “Required password” means non-null/non-empty only: do not reapply signup rules because authentication must test the exact credential. Dummy-hash lookup is not required; HTTP and logging behavior must still be indistinguishable.

## Verification

**Commands:**
- `./mvnw test` — expected: all Java 25 unit and PostgreSQL 18.6 Testcontainers tests pass; Flyway remains at V1/V2 and no additional persisted type appears.
- `mvn.cmd "-Djava.version=21" test` — passed locally: 53 tests, 0 failures/errors/skips, including PostgreSQL 18.6 Testcontainers; Java 21 override was required because Java 25 is not installed on this host.
- `docker run ... maven:3.10.0-eclipse-temurin-25 ./mvnw test` — passed authoritatively: 53 tests, 0 failures/errors/skips on Java 25.0.4.1 with PostgreSQL 18.6.
