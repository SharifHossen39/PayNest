---
title: 'Story 1.5: Access the Current User Profile Securely'
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

**Problem:** PayNest can issue JWTs, but it cannot yet authenticate bearer requests or let a signed-in User retrieve their own profile.

**Approach:** Add strict JWT consumption to the stateless Spring Security chain, install an immutable User-ID principal, and expose `GET /api/users/me` using the existing safe `UserResponse` DTO.

## Boundaries & Constraints

**Always:** Add only `GET /api/users/me`; derive identity exclusively from `Authorization: Bearer <JWT>`; accept only a valid HS256 signature, unexpired token, and decimal persisted User ID subject; resolve that User before authenticating; store only a feature-neutral User ID principal with no credentials or authorities; return exactly `id`, `name`, `email`, and `createdAt`; return the same shared-envelope `401 AUTHENTICATION_FAILED` / `Authentication failed` response for every missing, malformed, expired, or stale bearer token; keep signup, signin, and documentation routes public; remain stateless and deny unlisted product routes.

**Never:** Accept a caller-supplied User ID as ownership evidence; expose or place a JPA entity, password hash, JWT, claims, Wallet, or relationship graph in the response or `SecurityContext`; log bearer values, claims, secrets, credentials, parser details, SQL, or stack traces; add roles, sessions, refresh/logout/revocation, OAuth2 resource-server infrastructure, token persistence, migrations, entities, dependencies, or other product endpoints.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|----------------------------|----------------|
| Current profile | Valid Story 1.4 token for persisted User A | `200` with A's exact four-field `UserResponse`; no database write | N/A |
| Missing or malformed bearer | No header, wrong/blank scheme, malformed compact token, or ambiguous header | No controller invocation and empty security context | Generic `401 AUTHENTICATION_FAILED` |
| Invalid token | Tampered/wrong signature, non-HS256 algorithm, expired token, missing/non-numeric/overflow subject | Reject before profile access; invalid cryptographic tokens cause no User lookup | Generic `401 AUTHENTICATION_FAILED` |
| Stale identity | Valid token subject names a deleted/nonexistent User | Do not authenticate or disclose existence details | Generic `401 AUTHENTICATION_FAILED` |
| Ownership manipulation | Valid token for A plus query/path/header values naming B | Ignore caller-supplied identity noise and return only A | N/A |
| Public or unlisted route | Exact Auth/docs route, or an unlisted product route | Public routes remain public even with a stray bearer header; unlisted product routes remain denied | Existing safe/default route behavior |

</frozen-after-approval>

## Code Map

- `src/main/java/com/paynest/auth/security/JwtService.java` — preserve issuance; add strict verification that returns only the numeric User ID and hides JJWT failures.
- `src/main/java/com/paynest/auth/security/` — add the immutable principal, once-per-request bearer filter, and small JSON authentication entry point; no generic auth framework or interfaces.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — register the filter, preserve exact POST Auth allowlisting, permit Springdoc routes, require authentication only for exact `GET /api/users/me`, and keep deny-all fallback.
- `src/main/java/com/paynest/user/api/UserController.java` and `user/application/UserProfileService.java` — map the authenticated principal to the existing `UserResponse` through a read-only lookup; accept no ownership identifier.
- `src/main/java/com/paynest/user/api/UserResponse.java` and `user/persistence/UserRepository.java` — reuse unchanged safe mapping and inherited `findById`.
- `src/test/java/com/paynest/auth/security/` and `src/test/java/com/paynest/user/` — cover token verification, filter boundaries, ownership, safe errors, statelessness, and PostgreSQL-backed HTTP behavior.

## Tasks & Acceptance

**Execution:**
- [x] `auth/security/*` — implement inbound JWT verification, User resolution, ID-only authentication, safe `401` rendering, and exact route/filter configuration.
- [x] `user/api/UserController.java`, `user/application/UserProfileService.java` — implement the current-profile query using the authenticated principal and existing DTO.
- [x] `src/test/java/com/paynest/auth/security/*`, `src/test/java/com/paynest/user/*`, existing Auth HTTP tests — cover every matrix row and update the former unauthenticated `/api/users/me` expectation without weakening Auth route tests.

**Acceptance Criteria:**
- Given valid tokens for Users A and B, when each calls `GET /api/users/me`, then each receives only their own exact four-field profile and neither request mutates persistence.
- Given any rejected bearer form, when the protected endpoint is requested, then the selected safe `401` envelope is returned without sensitive output and without leaving authentication in `SecurityContext`.
- Given repeated protected requests, when they complete, then authentication is reconstructed from the bearer token without an HTTP session, roles, or stored token state.
- Given the completed story, when migrations and persisted types are inspected, then Flyway remains at V2 and only User and Wallet exist.

## Implementation Notes

- Extended the existing JWT component with strict HS256 verification, expiry enforcement, and canonical positive `BIGINT` subject parsing while keeping all parser failures internal.
- Added a stateless once-per-request filter that resolves the persisted User and installs only an ID-based principal with no credentials or authorities; exact Auth and Springdoc routes bypass bearer processing.
- Added safe filter-chain `401 AUTHENTICATION_FAILED` serialization and retained the deny-all fallback for unlisted product routes.
- Added the read-only current-profile controller/service flow using the existing four-field `UserResponse`; no schema, entity, dependency, or additional product endpoint was introduced.
- Verified every matrix row with focused unit/security tests and PostgreSQL-backed HTTP tests, then ran the full suite on Java 25.0.4.1 and PostgreSQL 18.6: 72 tests passed with Flyway remaining at V2.
- Review hardening now requires an expiration claim, publishes all intended documentation routes, adds the generic Bearer challenge, avoids materializing a User for the authentication existence check, and pins public-route, logout, no-write, and persisted-type behavior.
- Final Java 25.0.4.1/PostgreSQL 18.6 verification passed: 75 tests, 0 failures, errors, or skips; no review item was deferred.

## Spec Change Log

- 2026-10-12: Confirmed PostgreSQL-backed acceptance coverage now uses the guarded dedicated `paynest_test` database; container-based historical verification is superseded.

## Review Triage Log

| ID | Reviewer finding | Classification | Disposition |
| --- | --- | --- | --- |
| B1 | A correctly signed token without `exp` authenticates indefinitely. | medium | Patch: explicitly require `exp` and add a timeless-token rejection test. |
| B2 | `/v3/api-docs.yaml` is absent from both documentation allowlists. | medium | Patch: add the exact YAML route to the filter bypass and security allowlist, with HTTP coverage. |
| B3 | Bearer `401` responses omit `WWW-Authenticate: Bearer`. | low | Patch: this is a direct standards-compliance header with no new public behavior or branching. |
| B4 | Invalid credentials on an unlisted route produce `401` while missing/valid credentials produce `403`. | false | Rejected: the approved contract deliberately gives every malformed bearer a generic `401`; all three outcomes still deny the unlisted route. |
| B5 | A repository outage inside the filter bypasses the project `ApiError` handler. | low | Rejected: unexpected infrastructure failure handling is not the bearer-failure contract, Spring Boot still produces a safe error response, and a separate filter-level 500 mechanism would add disproportionate complexity. |
| B6 | Public-route policy is duplicated between the filter and authorization configuration. | low | Rejected: the two short lists serve different filter/authorization responsibilities; sharing matchers would add abstraction, while the concrete YAML omission is patched under B2. |
| B7 | Authentication materializes a full User including its hash before the profile service loads it again. | low | Patch: use the inherited existence query so the authentication check does not materialize unused sensitive columns. |
| B8 | Filter and MVC authentication errors construct their envelopes independently and could drift. | false | Rejected: current responses use the same `ApiError` type and exact tested values; no divergence exists, and a new response-factory abstraction is not justified. |
| B9 | The profile no-write test checks row counts but not mutation of existing rows. | medium | Patch: snapshot persisted User rows and assert them unchanged after both profile requests. |
| B10 | The local profile test counts migrations without asserting exact versions. | false | Rejected: the full suite validates the production V1 checksum against the fixture, migrates explicitly to V2, and separately fails if an extra migration changes the application count. |
| B11 | Table-name assertions do not prove that only User and Wallet are JPA entities. | medium | Patch: assert the JPA metamodel contains exactly `User` and `Wallet`. |
| B12 | Public-route and bearer-scheme variants are incompletely covered. | medium | Patch with V1, V2, and V4 for signup, documentation assets/YAML, and case-insensitive bearer handling; non-POST Auth routes remain intentionally non-public. |
| E1 | A signed token without expiration is accepted. | medium | Patch with B1; direct tracing confirms `exp` is currently optional. |
| E2 | An extremely large accepted TTL can overflow issuance arithmetic. | low | Rejected: this is a pre-existing pathological-configuration issue from Story 1.4, whose contract defines no arbitrary maximum lifetime. |
| V1 | Signup with a stray invalid bearer header is unverified. | medium | Patch: add a PostgreSQL-backed signup assertion proving normal `201` behavior. |
| V2 | Nested OpenAPI and Swagger UI bypasses are unverified. | medium | Patch: exercise Swagger configuration/UI assets and the YAML docs endpoint with an invalid bearer. |
| V3 | Disabling Spring Security's built-in logout endpoint is unpinned. | medium | Patch: assert `POST /logout` remains forbidden and does not redirect. |
| V4 | Case-insensitive bearer-scheme matching lacks a regression test. | low | Patch: exercise lowercase and mixed-case schemes with valid tokens. |
| V5 | Verification lacks a token-without-`exp` rejection test. | medium | Patch with B1/E1 in `JwtServiceTests`. |

## Design Notes

The filter bypasses exact public Auth/documentation routes so an irrelevant invalid bearer header cannot make a public resource private. Filter-layer failures use the existing `ApiError` shape through a small shared writer/entry point because controller advice cannot reliably handle pre-controller security failures. Keep services concrete; a read-only profile service may directly use `UserRepository`.

## Verification

**Commands:**
- `./mvnw test` — expected: Java 25 suite passes against PostgreSQL 18.6; JWT boundary and profile HTTP tests pass; Flyway remains V1/V2.
