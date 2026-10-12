# Epic 1 Context: Secure Account and Wallet Access

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Deliver the complete secure account journey: a new User registers with safely stored credentials and receives exactly one zero-balance USD Wallet in the same atomic operation, can sign in for a time-limited JWT, and can use that token to retrieve only their own safe profile and Wallet. This epic also establishes the minimum runnable Spring Boot, PostgreSQL, Flyway, persistence, and stateless-security foundation on which later money movement and Transaction features depend.

## Stories

- Story 1.1: Establish the Runnable PayNest Foundation
- Story 1.2: Persist Users and Their One-to-One Wallets
- Story 1.3: Register a User with an Atomic Wallet
- Story 1.4: Sign In and Receive a JWT
- Story 1.5: Access the Current User Profile Securely
- Story 1.6: View the Authenticated User's Wallet

## Requirements & Constraints

- Keep the MVP as one Maven-built Spring Boot application, one deployment unit, and one PostgreSQL database. Epic 1 exposes only `POST /api/auth/signup`, `POST /api/auth/signin`, `GET /api/users/me`, and `GET /api/wallet`; do not add Wallet funding or other convenience APIs.
- Registration validates and stores a trimmed 2-100 character name, a normalized unique email of at most 254 characters, and an unmodified 8-72 character Unicode password. A successful request returns a safe User profile and `0.00 USD` Wallet; duplicate normalized emails return the stable conflict contract.
- User creation, password encoding, and exactly one Wallet creation form one ACID operation. Any failure commits neither record, and the database uniqueness constraints remain authoritative under concurrent requests.
- Sign-in normalizes email, verifies credentials, and returns a signed bearer token plus matching expiry metadata. Unknown email and incorrect password must be indistinguishable to clients.
- All non-Auth product operations are denied by default without a valid token. Ownership comes only from the verified principal; current-profile and Wallet APIs accept no caller-supplied User or Wallet identifier.
- Responses use dedicated DTOs and never expose password hashes, JWT claims, persistence relationships, or entity graphs. Known failures use the shared safe error envelope and stable codes; no response or log may reveal credentials, tokens, secrets, SQL, stack traces, or internal class details.
- Money is `BigDecimal`/`NUMERIC(19,2)` and Wallet currency is USD only. Timestamps are `Instant`/`TIMESTAMPTZ`, serialized as ISO 8601 UTC, with an injectable `Clock` for deterministic tests.
- Verify HTTP, security, JPA mappings, Flyway, constraints, concurrent duplicate registration, and rollback against Testcontainers PostgreSQL; do not use H2 as evidence for database behavior.

## Technical Decisions

- Use Java 25 LTS, Spring Boot 4.1.1, Maven Wrapper 3.10.0, PostgreSQL 18.6, JJWT 0.13.0, and the Boot-managed persistence/test stack. Keep dependencies to the approved Web MVC, Validation, Data JPA, Security, Flyway, PostgreSQL, JWT, OpenAPI, and test libraries; avoid speculative frameworks and abstractions.
- Organize under `com.paynest` by feature (`auth`, `user`, `wallet`, `transfer`, `transaction`, `common`) with API, application, domain, and persistence responsibilities kept separate. Controllers translate HTTP; concrete application services own use cases and transaction boundaries; repositories remain focused persistence components.
- Persist only `User`, `Wallet`, and later `Transaction`, using generated `BIGINT` IDs. Wallet owns a required lazy unidirectional one-to-one relationship to User through unique `wallets.user_id`; User has no inverse Wallet field. Configure no cascade delete or orphan removal and explicitly persist both records during registration.
- Flyway alone evolves the schema and Hibernate uses `ddl-auto=validate`. The foundation consumes no domain migration; add `V1__create_users.sql` with the User mapping, then `V2__create_wallets.sql` with the Wallet mapping. Shared migrations are append-only and use named unique, foreign-key, USD-only, and non-negative-balance constraints.
- `RegistrationService` in `auth.application` owns normalization, password encoding, User insertion, and Wallet insertion inside one transaction. It may directly use the User and Wallet repositories to keep this cross-feature atomic coordinator readable.
- Store passwords with a `DelegatingPasswordEncoder` using `{pbkdf2@SpringSecurity_v5_8}` and the complete password input. Never store, transform, truncate, return, or log the plaintext value.
- Issue HS256 JWTs with a Base64 secret of at least 256 bits from `PAYNEST_JWT_SECRET`. Tokens contain only decimal User ID in `sub`, `iat`, `exp`, and optionally a fixed issuer; default lifetime is 60 minutes via `PAYNEST_JWT_TTL`. Missing or weak signing material fails startup outside explicit tests.
- Use a once-per-request JWT filter to validate algorithm, signature, expiry, and numeric subject, resolve the persisted User, and install an immutable feature-neutral `AuthenticatedUser` in `SecurityContext`. Security is stateless; Auth and documentation routes are public, all other product routes require authentication, and the MVP has no roles, refresh tokens, logout, OAuth2 server, or session/token persistence.

## Cross-Story Dependencies

Story 1.1 provides the build, database, migration, configuration, and test foundation. Story 1.2 adds User before Wallet so `V1` remains valid when `V2` adds the owning foreign key. Registration depends on both mappings and establishes the credentials and Wallet invariant required by sign-in. The JWT principal flow then enables the current-User and current-Wallet queries. Epics 2 and 3 depend on the trusted User identity and one-Wallet-per-User invariant established here.
