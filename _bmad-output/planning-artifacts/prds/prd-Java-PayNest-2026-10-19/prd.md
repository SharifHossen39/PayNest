---
title: 'PayNest Frontend PRD Extension'
status: final
created: 2026-10-19
updated: 2026-10-19
phase: 'Frontend extension after completed backend MVP'
---

# PayNest frontend PRD extension

## 1. Purpose and authority

Give PayNest a small browser interface for its completed wallet backend. This document guides UX, architecture, and frontend stories for one developer learning through a realistic application.

The [backend PRD](../prd-Java-PayNest-2026-10-06/prd.md) remains authoritative for business rules and API contracts. Its API-only/UI exclusion describes the completed backend MVP; this separately authorized phase adds a frontend. Existing requirements and completed backend epics are preserved. The user selected Angular and Tailwind CSS; implementation considerations are in [addendum.md](addendum.md).

**FE-OQ-1 — Resolved: reload authentication.** Sharif selected retaining sign-in across browser reloads until the existing JWT expires. Reload must not extend the token's lifetime. Token-storage design belongs to architecture and must not silently introduce new backend authentication capabilities.

## 2. Vision, user, and success

A PayNest User can register, sign in, inspect their USD Wallet, transfer money to another registered User, and read their Transactions without manually constructing API requests. The primary learner can understand and test this complete browser-to-database journey.

Success means two demonstration Users can complete this journey against the real backend, see exact balances and participant-specific receipts, and recover clearly from validation, authentication, and transfer failures. A zero-balance Wallet is a valid initial state. Development funding remains an explicit manual SQL fixture.

The counter-metric is scope growth: screen count, dependencies, dashboards, and frontend abstractions are not measures of progress. No production banking, adoption, or availability targets are introduced.

## 3. Vocabulary and captured journeys

- **User:** an existing PayNest account; all Users have equal permissions.
- **Wallet:** the User's single USD balance, created during registration.
- **Transfer:** the business operation that atomically debits and credits two Wallets.
- **Transaction:** the immutable completed Transfer receipt, visible only to its participants.
- **Sign out:** discard frontend authentication and private client state; this does not revoke a JWT on the server.
- **Unknown outcome:** a frontend recovery state after an ambiguous response loss, never a persisted Transaction status.

These journeys adapt the existing backend journeys to the proposed browser screens:

- **FE-UJ-1:** Amina signs up, sees account creation confirmed with a zero USD Wallet, then signs in and opens her Dashboard.
- **FE-UJ-2:** After Amina's demonstration Wallet is funded manually, she enters Rahim's email and a USD amount, reviews the intended Transfer, submits once, and sees the confirmed receipt and updated balance.
- **FE-UJ-3:** Rahim signs in, opens Transaction history, and reads the same receipt as RECEIVED. Amina sees it as SENT.
- **FE-UJ-4:** Amina reloads her signed-in browser tab and remains signed in while her JWT is valid. If it expires, she signs in again. If a Transfer response was lost, the interface explains that its outcome is unknown and guides her to review Wallet/history without replaying the request.

## 4. Scope and API boundary

| Screen | Existing product API operations |
|---|---|
| Sign up | POST /api/auth/signup |
| Sign in | POST /api/auth/signin |
| Dashboard: profile and Wallet | GET /api/users/me; GET /api/wallet |
| Send money | POST /api/wallet/transfers |
| Transaction history | GET /api/transactions |
| Transaction detail | GET /api/transactions/{id} |

Navigation and local sign-out controls support these six screens; they are not additional business capabilities. No funding control, recipient directory, profile editing, custom sorting/filtering, charts, or reporting is included.

## 5. Functional requirements and acceptance

### Accounts and authentication

**FE-FR-1 — Sign up (FE-UJ-1).** Collect name, email, and password. Reflect backend validation: trimmed name 2–100 characters, valid normalized email up to 254 characters, and password 8–72 characters preserved exactly. Client checks must not reject values accepted by the corresponding backend rules.

Acceptance: valid registration receives 201, confirms one zero USD Wallet, and offers sign-in. It must not claim that signup authenticated the User. Invalid fields show useful feedback; duplicate email shows the existing 409 result. No password is retained after completion/navigation or logged.

**FE-FR-2 — Sign in (FE-UJ-1).** Collect email and password, use the returned bearer JWT and expiry metadata, and open the Dashboard on success. Do not apply signup-only password length rules to sign-in.

Acceptance: 200 allows protected reads. Invalid credentials show the same generic message for an unknown email and a wrong password. The submit control prevents concurrent repeated submissions.

**FE-FR-3 — Authentication lifecycle (FE-UJ-4).** Unauthenticated protected navigation leads to sign-in. Protected 401 responses clear private client state and explain that sign-in is required. Sign out clears the credential and private views, including data from the previous User. Signing in must never replay a Transfer.

Acceptance: reloading a signed-in browser tab preserves authentication while its JWT remains valid, without another sign-in or a new token. Reloading after expiry requires sign-in; reload never extends expiry. Explicit sign-out or a protected 401 clears the saved credential and private state, so subsequent reloads cannot restore that authentication. Private screens are inaccessible through normal navigation after sign-out/expiry, delayed responses from an earlier User cannot repopulate them, and no server-side revocation is promised. The storage mechanism remains an architecture decision.

### Wallet and Transfer

**FE-FR-4 — Dashboard (FE-UJ-1).** Show the current User's name/email and the current Wallet's balance and USD currency, using authenticated identity rather than a selectable User/Wallet ID.

Acceptance: a new Wallet displays 0.00 USD truthfully. A User can refresh displayed Wallet data. Loading and failed retrieval are distinguishable from a zero balance; private data is cleared on identity change. A failed refresh must not present an old balance as newly confirmed.

**FE-FR-5 — Send money (FE-UJ-2, FE-UJ-4).** Collect recipient email and an exact USD amount. Require a valid email and a positive amount with at most two entered fractional digits, up to 99999999999999999.99; extra fractional zeros also count. Do not round invalid input. Explain that the backend decides recipient existence, self-transfer, available funds, and concurrency outcomes.

Acceptance: show recipient/amount for review before submission; allow only one in-flight submission. Confirm completion only after 201 with COMPLETED, show its receipt and balanceAfter, and refresh affected client views. Failed refresh after a confirmed Transfer must not relabel it as failed. Display validation, missing-recipient, insufficient-funds, self-transfer, and conflict responses clearly.

Never automatically retry or queue Transfer POSTs. On a timeout or lost response, explain that completion is unknown, offer Wallet/history review, and require deliberate action before another submission. Similar history rows are not proof of which request committed. Unknown outcome is a UI state only; no PENDING/FAILED Transaction lifecycle is added.

### Receipts

**FE-FR-6 — Transaction history (FE-UJ-3).** Show SENT/RECEIVED, counterparty name/email, exact amount, USD, COMPLETED status, and date/time. Use page=0 and size=20 by default, with backend pagination and fixed newest-first ordering.

Acceptance: both participants see the same Transaction ID with opposite directions. Provide bounded previous/next navigation using returned pagination metadata; empty history is not an error. Pagination inputs respect page >= 0 and size 1–100. Clearly show a recoverable loading/error state.

**FE-FR-7 — Transaction detail (FE-UJ-3).** Open a selected receipt using its exact, unmodified identifier; show direction, sender/recipient, amount, USD, COMPLETED, and date/time.

Acceptance: either participant can read it. Unknown and inaccessible IDs show the same not-found outcome without disclosing another User's data. Invalid identifiers show a safe error. Provide navigation back to history.

### Shared experience

**FE-FR-8 — Feedback and navigation.** Provide clear navigation among the authenticated screens, pending-submit feedback, field errors, and page-level errors. Render the existing ApiError contract rather than database/framework details.

Acceptance: fieldErrors map to inputs where relevant; a safe summary remains when no field mapping exists. Distinguish invalid credentials, expired authentication, not found, conflict, business rejection, server failure, and offline/unknown outcomes. Failed read requests can be retried explicitly; Transfer retry rules remain those of FE-FR-5. New protected screens must not bypass backend authorization.

## 6. Quality and integration requirements

- **FE-NFR-1 — Responsive and accessible:** forms and navigation remain usable on phone and desktop viewports, with keyboard operation, associated labels, visible focus, readable errors, and text alternatives to color-only statuses. Exact visual tokens/layouts belong to UX.
- **FE-NFR-2 — Numeric fidelity:** preserve cents across the entire supported backend monetary range and preserve BIGINT identifiers exactly. Format USD to two decimals; never compute authoritative balances in the browser or silently change numeric API fields to strings. Architecture must resolve lossless parsing/serialization before API client stories.
- **FE-NFR-3 — Privacy:** no signing keys/database credentials in frontend configuration; no passwords, JWTs, or full sensitive payloads in logs, URLs, or analytics. Scope authentication to the intended PayNest API. Client navigation guards supplement server authorization.
- **FE-NFR-4 — Testability:** verify forms, reload persistence before JWT expiry, sign-in required on reload after expiry/sign-out/protected 401, private-state clearing, exact money/ID boundaries, duplicate-submit prevention, unknown Transfer outcomes, pagination, and ownership-safe errors. A real-backend browser smoke journey must cover all six screens with manual/test-only funding.
- **FE-NFR-5 — Delivery:** support direct local frontend/backend development and document build/start/test steps alongside the existing runbook. Keep one Spring Boot backend, one PostgreSQL database, three persisted entities, and seven product API operations. Browser assets/navigation may need narrowly scoped integration configuration; API protections must remain intact.

## 7. Explicit non-goals

No deposit/withdrawal/funding UI or API, banks/gateways/cards, KYC/OTP, email verification/reset, refresh tokens, server logout/revocation, admin/roles, notifications, multi-currency, refunds/cancellations, recurring payments, offline Transfer replay, native mobile/PWA delivery, Docker/container tooling, or microservice infrastructure. Existing backend exclusions remain binding. Aesthetic decisions and exact Angular service/DTO organization are downstream design work.

## 8. Handoff

FE-OQ-1 is resolved; no product decisions block handoff. Next: UX design, architecture update, and frontend epics/stories appended after completed backend work. No implementation is authorized by this requirements document alone.

## 9. Assumptions and deferred design

The six-screen scope and Angular/Tailwind selection were supplied/approved in the conversation. No additional business-feature assumptions are introduced. Visual theme, exact browser support baseline, toolchain versions, client state organization, and asset-serving details are deferred to UX/architecture and must preserve this PRD's behavior and backend boundary. Cross-tab and browser-restart behavior are not specified by the reload choice; architecture must document these behaviors without treating them as additional product requirements.
