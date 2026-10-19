---
title: PayNest Frontend Epic Breakdown
status: final
created: 2026-10-19
updated: 2026-10-19
stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
  - step-03-create-stories
  - step-04-final-validation
currentStep: complete
sourceDocumentsConfirmed: true
extractionStatus: confirmed
epicDesignStatus: approved
storyDesignStatus: approved
epic5StoryPlanningStatus: confirmed
epic6StoryPlanningStatus: confirmed
validationStatus: passed
validationOpenChecks: 0
workflowStatus: complete
currentEpic: 6
currentStory: '6.6'
approvedStories:
  - '5.1'
  - '5.2'
  - '5.3'
  - '5.4'
  - '5.5'
  - '5.6'
  - '5.7'
  - '5.8'
  - '6.1'
  - '6.2'
  - '6.3'
  - '6.4'
  - '6.5'
  - '6.6'
baselineEpicMax: 4
nextEpicNumber: 5
inputDocuments:
  - prds/prd-Java-PayNest-2026-10-19/prd.md
  - prds/prd-Java-PayNest-2026-10-19/addendum.md
  - architecture/architecture-Java-PayNest-2026-10-19/ARCHITECTURE-SPINE.md
  - ux-designs/ux-Java-PayNest-2026-10-19/DESIGN.md
  - ux-designs/ux-Java-PayNest-2026-10-19/EXPERIENCE.md
  - epics.md
---

# PayNest Frontend — Epic Breakdown

## Overview

This document decomposes the finalized frontend PRD, approved UX contract, and frontend architecture into implementable frontend work. The requirements inventory and two-epic grouping are approved. All fourteen stories (5.1–5.8 and 6.1–6.6) are individually approved for planning, and both epic story plans are confirmed. Formal final validation passed with zero open planning checks, and the user selected Complete Workflow. This document is final and ready to guide development. Planning completion does not authorize implementation.

The user confirmed these inputs. [Backend Epics 1–4](epics.md) are a read-only completed baseline, not work to regenerate. Frontend numbering begins at Epic 5. This separate output preserves the existing backend epic document. No sprint plan or product implementation is created here.

The frontend remains six screens over seven existing API operations. One Spring Boot deployment, one PostgreSQL database and exactly User, Wallet and Transaction remain binding. Registration and transfers retain backend atomicity/concurrency guarantees. Funding stays manual/test-only. No public funding endpoint, Docker, new business entities, refresh tokens, cookie/BFF authentication or microservice infrastructure is added.

## Requirements Inventory

Source identifiers FE-FR-1..8 and FE-NFR-1..5 are preserved. FE-AR and UX-DR identifiers below organize existing architecture/UX requirements for planning; they do not introduce new scope. Full source acceptance criteria and technical contracts remain authoritative.

### Functional Requirements

FE-FR-1: Sign up using name, email and password. Match backend Unicode-aware normalization/validation: trimmed name 2–100 characters, valid normalized email up to 254, password 8–72 preserved exactly. Do not reject values valid under the corresponding backend rules. A 201 confirms one zero USD Wallet and offers explicit sign-in without authenticating the User. Present field errors and duplicate-email 409 clearly; never log or retain the password after completion/navigation.

FE-FR-2: Sign in using email/password and the existing TokenResponse. A successful 200 enables protected reads and opens Dashboard using the issued bearer JWT/expiry. Do not apply signup-only password-length restrictions. Unknown email and wrong password share a generic invalid-credentials message; prevent concurrent repeated submissions.

FE-FR-3: Guard private navigation and preserve signin across same-tab reload while the existing JWT is valid, without issuing another token or extending expiry. Expiry, explicit local sign-out and current protected 401 clear saved credentials/private data and require signin. Prevent previous-identity delayed responses from repopulating private screens. Signin never replays a transfer; sign-out does not revoke a server JWT. Use the approved architecture's sessionStorage mechanism and documented learning-only risk.

FE-FR-4: Dashboard displays current User name/email and current Wallet balance/USD from authenticated identity, without selectable owner IDs. Display 0.00 USD only after a successful zero-balance response. Support explicit refresh; loading, unavailable data and stale/failed refresh must not appear as a newly confirmed zero/current balance. Clear private data on identity change.

FE-FR-5: Send money using recipient email and exact positive USD amount, maximum 99999999999999999.99 and at most two entered fractional digits; extra zeros still count and invalid input is never rounded. Review recipient/amount before one explicit submission; only the backend decides recipient existence, self-transfer, funds and concurrency. Confirm only a valid 201/COMPLETED response, show receipt/balanceAfter and refresh affected reads; refresh failure cannot relabel confirmed completion as failure. Explain missing recipient, validation, insufficient funds, self-transfer and conflict. Never automatically retry, queue or replay POSTs. Lost/ambiguous response produces unknown-outcome recovery with Wallet/history navigation and deliberate fresh action; matching history is not proof of which request committed. Add no persisted failed/pending status.

FE-FR-6: Show participant-relative history with SENT/RECEIVED, counterparty name/email, exact USD amount, COMPLETED and timestamp. Both participants see the same exact Transaction ID with opposite directions. Request page0/size20 by default, respect API bounds page≥0/size1–100 and server newest-first ordering, and provide bounded Previous/Next from response metadata. Successful empty history differs from loading/error; errors permit safe read recovery.

FE-FR-7: Open a selected Transaction using its exact unmodified identifier. Show direction, sender/recipient, exact amount, USD, COMPLETED and timestamp. Either participant can read it; unknown and inaccessible IDs share the same not-found presentation without ownership disclosure. Invalid IDs receive safe feedback; provide Back to Transactions.

FE-FR-8: Provide authenticated navigation, pending-submit feedback, relevant fieldErrors and safe page summaries. Use existing ApiError rather than framework/database internals; retain a summary when fields do not map. Distinguish credentials/expiry, not found, conflict, business rejection, server failure and offline/unknown outcomes. Read retries are explicit; transfer retry rules remain FE-FR-5. Client navigation never replaces backend authorization.

### NonFunctional Requirements

FE-NFR-1: Forms/navigation work on phone and desktop with keyboard access, associated labels, visible focus, readable errors and text equivalents for color-only statuses. Implement the approved UX tokens/layouts rather than redesigning them.

FE-NFR-2: Preserve exact cents across the full backend monetary range and all BIGINT identifier digits. Display two USD decimals without browser-authoritative balance arithmetic or changing numeric JSON wire fields to strings. Establish the lossless parsing/serialization boundary before feature API consumers.

FE-NFR-3: Put no database/signing secrets in frontend configuration and no passwords, tokens or sensitive payloads in URLs/logs/analytics. Attach credentials only to the intended protected PayNest operations. Guards supplement server ownership enforcement.

FE-NFR-4: Verify forms, reload before/after expiry/sign-out/401, private-state cleanup, numeric boundaries, duplicate prevention, unknown transfer recovery, pagination and ownership-safe errors. Include a real-backend browser journey through all six screens using manual/test-only funding; mocked browser tests alone do not prove integration.

FE-NFR-5: Document direct local frontend/backend build/start/test and final combined delivery alongside the existing runbook. Preserve one Spring Boot backend, one PostgreSQL database, three persisted entities and seven product operations. Add only narrowly scoped browser asset/navigation integration; retain API protections.

### Additional Requirements

**Starter prerequisite:** the first frontend implementation story must establish the official Angular CLI foundation. This is frontend work beginning with Epic 5, not a reopening of backend Story 1.1. It uses standalone/strict/zoneless components, routing, CSS/Tailwind, Vitest, npm, SSR disabled and no nested Git repository (architecture AD-14, AD-21 and Stack).

- FE-AR-1 — Preserve parent AD-1..12: existing backend feature ownership, local ACID/ordered PostgreSQL locks, schema/Flyway ownership, completed-only records, trusted bearer identity, safe DTOs and guarded test database. Frontend work adds no domain migration or business API (AD-13).
- FE-AR-2 — Use `frontend/` with feature-organized auth, Dashboard, transfer and Transaction pages, core API/session boundaries and genuinely reusable UI. Local Signals own page/session state and RxJS owns HTTP lifecycles. No SSR, global-store framework, generic repository/service-interface scaffolding or mandatory one-class-per-UX-pattern (AD-14/20).
- FE-AR-3 — Start from the verified exact baseline: Node24.20.0/npm11.19.0; Angular app packages/CLI/build/compiler-cli22.2.3; TypeScript6.0.3; RxJS7.8.2/tslib2.8.1; Tailwind/plugin4.3.3/PostCSS8.5.31; lossless-json4.3.2; Vitest5.0.5/jsdom30.1.3; Playwright1.64.1. Commit one lockfile and use npm ci. Do not substitute incompatible latest TypeScript or add a separate Vite runtime dependency (Stack).
- FE-AR-4 — Establish one root-scoped PayNestApi, one root-scoped SessionService, shared readonly DTOs and ApiFailure before feature-page consumers. Preserve the architecture's seven API method names/signatures, cold Observable success/error contract and Promise-based session coordination. API owns no screen/auth state; features do not import another feature's page/internal state; shared presentation has no HTTP/storage (AD-14; Shared client contract seed).
- FE-AR-5 — Keep construction free of HTTP/bootstrap side effects. Invoke restore through an explicit post-construction application initializer; interceptors consult SessionService at request time, avoiding circular construction and duplicate route-local session providers (AD-14).
- FE-AR-6 — Request success/error bodies as HTTP text; lossless-json parses numeric lexemes before runtime DTO validation. Map money to canonical two-decimal strings, IDs to positive decimal strings≤9223372036854775807, and totalElements to nonnegative int64 strings. Only validated bounded status/page/size/totalPages become Number. No ordinary JSON parse, Number/valueOf/parseFloat, number-input coercion or numeric currency pipe on money/ID paths (AD-15).
- FE-AR-7 — Validate amount entry before normalization: plain decimal, no grouping/exponent/sign, positive, maximum19,2 and at most two entered fractional digits. Exact response exponent normalization must be bounded before expansion; reject nonzero sub-cent precision and out-of-range tokens. Send numeric fields through LosslessNumber/library stringify as a raw JSON string body with application/json, not automatic object serialization; preserve actual strings as JSON strings (AD-15).
- FE-AR-8 — SessionService alone writes/clears the sessionStorage credential record and separate recovery marker. Persist only accessToken/expiresAt/nonsecret per-login nonce in the credential record, no passwords/profile/Wallet/receipt/draft. Sharif accepted the learning-only XSS-readable storage risk; do not claim production banking security or introduce cookies/refresh/session infrastructure (AD-16).
- FE-AR-9 — On bootstrap validate stored shape/expiry, use restoring/anonymous/authenticated phases, and verify current User before private rendering. Restore network/server failure offers explicit retry without stale private content. Storage denial produces visible safe failure, not silent memory-only fallback. Recheck expiry before protected dispatch and on timer/focus/visibility (AD-16/17).
- FE-AR-10 — Document origin/tab isolation, no tab synchronization, opener/duplicate/restore limitations, and noopener for app-created new-tab links. Do not promise closing/restarting always destroys credentials. Local sign-out affects this tab, not server revocation; dev and packaged origins require separate signin (AD-16).
- FE-AR-11 — Advance login generation for signin attempts, sign-out, expiry/current protected401; old successes/errors cannot install credentials, navigate a new session or log out a newer User. Scope bearer attachment to the exact five protected same-origin method/path operations, never auth/docs/assets/arbitrary URLs. Public signin401 is generic credentials failure. Keep Angular escaping; no trust bypass or sensitive logging (AD-17).
- FE-AR-12 — Before one transfer subscription, enforce the cross-route per-login in-flight guard and successfully store a minimal login-nonce/local-attempt-key/boolean recovery marker. Store no token copy or replayable payload. Marker persistence failure prevents dispatch. Ticket settlement is single/idempotent; only the active ticket/generation may clear its marker. Local attempt keys are not server idempotency keys or persisted entities (AD-18).
- FE-AR-13 — Use the shared outcome classification: valid201/COMPLETED→completed; valid ApiError400/VALIDATION_ERROR,404/USER_NOT_FOUND or WALLET_NOT_FOUND,409/CONFLICT,422/SELF_TRANSFER_NOT_ALLOWED or INSUFFICIENT_BALANCE→rejected. Valid401/AUTHENTICATION_FAILED or INVALID_TOKEN and403/ACCESS_DENIED→rejected; current401 also ends auth. Every5xx, unmapped pair, malformed/unexpected body, timeout/network loss or post-dispatch cancellation→unknown, never assumed rollback (AD-18).
- FE-AR-14 — Restore a same-auth recovery marker as an unknown warning after reload/return, never a command. Confirmed success/known rejection clear it; uncertainty retains it. Explicit fresh intent may acknowledge unknown only with no transfer in flight. Expiry/401 clears private marker/credentials but offers generic no-replay explanation; sign-out clears both keys. No prior-user facts or late attempt may reappear in a newer session (AD-18).
- FE-AR-15 — Give each read surface its own latest request generation plus route/page key, independent of login generation. Superseded detail/history/refresh success, error and loading completions cannot overwrite newer state. Keep independently failing Dashboard reads, safe stale labels and immediate detail clearing (AD-19).
- FE-AR-16 — Use the existing DTO shapes: Wallet has balance/currency/createdAt and no ID; transfer is transaction+balanceAfter; history is content/page/size/totalElements/totalPages/last with no first field. Detail sender/recipient and history/transfer counterparty remain distinct. No duplicate feature-owned response interfaces, invented fields or changed numeric wire types (AD-14/19; shared contract).
- FE-AR-17 — Decode failures centrally to ApiFailure kind http/network/contract/client, optional validated status/code, safe message/fieldErrors. Observable failures and session Promise rejections share this shape; local failure codes do not become new backend error codes. Feature pages do not parse raw errors (AD-19; shared contract).
- FE-AR-18 — Add Vitest/TestBed coverage for pure helpers, raw-literal numeric fixtures, runtime contracts, forms, HTTP/interceptors and session/transfer state. Cover full money/ID/count boundaries, exponent processing, storage denial, late old-session and same-session responses, duplicate subscription, marker-before-dispatch, cancellation/malformed/unknown outcomes and confirmed success with failed refresh (AD-22).
- FE-AR-19 — Use Playwright Chromium for six-screen keyboard/narrow-screen and HTTP edge journeys. Run a separate real-backend smoke against explicitly configured guarded developer-managed paynest_test with manual/test-only funding; never reset normal development data. Retain existing PostgreSQL acceptance, no H2/Testcontainers/Docker. Disable automatic transfer/test retries and make deliberate reruns repeatable through explicit fixtures (AD-22).
- FE-AR-20 — During development proxy only /api/** from Angular localhost4200 to existing Spring localhost8080, with relative /api calls and no required CORS expansion. Final delivery packages the browser build inside the existing executable JAR: base href/, index in classpath/static/index.html, resource URLs and remaining output under /ui-assets/. Keep generated assets in build output, not committed Java resources (AD-21).
- FE-AR-21 — Combined packaging is an explicit opt-in step using installed Node/npm and npm ci; ordinary backend work/tests must not require Node. Add only public GET HTML navigation/generated assets, excluding reserved /api,/v3,/swagger-ui,/actuator,/error,/ui-assets from SPA fallback. Missing assets cannot become HTML; preserve unknown API path/method deny and JSON errors regardless of Accept. Test direct deep links, unknown UI routes, missing assets and reserved-path rejection against the combined JAR; no blanket public /** matcher or second runtime server (AD-21/22).
- FE-AR-22 — Extend direct-local runbook with frontend prerequisites/build/test/dev proxy and combined-JAR workflow; frontend configuration is public and backend secrets remain external. No automatic toolchain installers or new infrastructure. Preserve existing API/security/OpenAPI contracts and backend regression checks. Target the documented Angular/Tailwind browser-support intersection, not new legacy-browser/PWA/public-hosting work (AD-21/22; Deferred).

### UX Design Requirements

The following are testable visual/behavioral work items from both UX spines. Component names identify patterns whose visual and behavioral contracts must be met; they do not require separate Angular classes without a reuse need.

UX-DR1: Map the complete approved color token set into global CSS/Tailwind: off-white canvas, white surfaces, dark ink/muted text, teal primary/hover/tint, error and warning pairs, control-border/divider/focus. Use colors for their specified roles, with visible labels for status and one primary action per task stage. Keep light-only design and no external font/component framework.

UX-DR2: Implement system-sans title 28px, heading 20px, body 16px, label/meta 14px and Wallet-money 32px roles with the defined weights/line heights. Use tabular money digits; other amounts use body/heading. Display full exact money with two decimals/USD and full IDs; wrap instead of shrinking, truncating or abbreviating. Names/emails also wrap.

UX-DR3: Apply the 4px spacing scale, named gaps, 6px control/10px task/14px auth-balance radii and pill radius only for compact status. Use surface tone/dividers rather than decorative shadows, gradients, fake cards or celebration. Loading regions reserve space so controls do not jump.

UX-DR4: At ≥1024px show a 224px sidebar, main max 1120px and 32px gutters; below 1024px use top brand/sign-out plus visible wrapping text navigation and 20px gutters, without a hidden drawer. Preserve navigation and content order across layouts.

UX-DR5: Auth forms center at max 440px; Send money/detail use max 640px and stack on narrow screens. Dashboard shows balance/refresh before profile. Send uses one task column with same-route entry/review/result, history a semantic responsive list, and detail a description list. Root/unknown UI navigation is a redirect, not a seventh screen.

UX-DR6: Implement app-shell pattern with Dashboard/Send money/Transactions, secondary Sign out, visible active destination plus aria-current, and Transactions active for detail. Navigation never submits transfers; local sign-out clears private state and late-response eligibility.

UX-DR7: Implement auth-form pattern with vertical associated fields, same-width error/success regions and pending-submit lock. Preserve signup-success versus signin-success behavior; do not retain passwords after completion/departure or apply signup-only rules to signin.

UX-DR8: Implement action-button pattern with native button/link semantics, teal primary/outlined secondary, at least 44px height, readable disabled state and visible 2px focus/2px offset. Pending keeps useful status text and prevents repeated activation; Enter cannot advance review and confirm in the same event.

UX-DR9: Implement form-field pattern with persistent labels, control-border/error/focus treatments, adjacent helper/errors and programmatic descriptions. Password show/hide is keyboard accessible and never changes/trims the value. Match backend normalization/Unicode validation without discarding relevant entered text.

UX-DR10: Implement balance-panel pattern with exact USD balance and refresh action, then current profile below. Label loading/unavailable/stale states; never substitute zero for a missing response. Profile and Wallet failures/retries remain independent.

UX-DR11: Implement transfer-review pattern showing exact intended email and validated normalized two-decimal amount; allow Edit to restore field focus, and one final confirmation that locks edits. Review performs no POST and is inline, not a modal.

UX-DR12: Implement transfer-result pattern with clearly distinct confirmed completion, rejection and unknown warning. Confirmed receipt/balanceAfter is at-transfer evidence, not a computed current balance. Unknown offers Wallet/history navigation and deliberate fresh action, never Retry transfer. Failed post-success refresh retains completed copy plus a current-balance refresh explanation.

UX-DR13: Implement transaction-list pattern as one semantic list: direction, counterparty name/email, full USD amount, COMPLETED, timestamp and explicit receipt link. Desktop grid rows become stacked mobile rows without dropping facts. No optimistic/pending/failed rows, filtering or client sorting.

UX-DR14: Implement receipt-detail pattern with readable term/value pairs, full exact ID and returned participant facts, separate direction/status, and Back to Transactions. On phones terms stack above values; detail cannot substitute missing sender/recipient facts with history-only counterparty data.

UX-DR15: Implement status-label pattern with textual COMPLETED and the teal tint; SENT/RECEIVED remains separate viewer-relative direction. Color alone is never status evidence and no additional Transaction status palette is introduced.

UX-DR16: Implement feedback-panel pattern with safe summary, actionable explanation, mapped adjacent field errors and only applicable recovery controls. Use error versus warning treatment according to outcome, urgent alert once and polite read/loading status otherwise; a transient toast cannot be the only receipt.

UX-DR17: Implement pagination pattern with visible page number backend+1, bounded Previous/Next, readable disabled/loading states and wrapping controls. Fix visible page size 20; no size selector. Retain loaded page on failed navigation and announce successful page changes without selecting a receipt or losing pagination focus.

UX-DR18: Signup must cover empty/focus/client-invalid/backend-field/pending/201 confirmation/duplicate409/server/offline/response-loss states. Confirmation replaces the form and offers signin with zero-Wallet copy, not auto-login. Ambiguous registration warns it cannot be confirmed and offers signin before deliberate new signup; no automatic replay.

UX-DR19: Signin must distinguish empty/focus/invalid input, pending, generic credential failure, network/server failure and expired-session explanation. Show no previous User private data, clear password on completion/navigation, and never auto-resubmit. Successful authenticated bootstrap leads to Dashboard.

UX-DR20: Dashboard has independent cold-loading/data/unavailable/error/read-retry regions. A successfully loaded zero is 0.00 USD; refresh labels last-confirmed data stale until success, including offline/failure. Identity change clears old private content; expiry or current protected 401 also routes to signin.

UX-DR21: Send money explicitly covers entry, input-invalid, valid review, submitting, completed receipt, known rejection and unknown recovery. Reject invalid email/nonpositive/over-maximum/excess fractional input before review; backend owns existence/self/funds/concurrency. Warn after ambiguous dispatch, preserve only minimal scoped warning over reload, never draft/command; auth loss gives safe no-replay explanation and removes private data.

UX-DR22: History distinguishes cold load, populated and successful-empty states, focus, pagination fetch/refresh/error/offline and auth loss. Refresh/new entry after confirmed transfer starts page0. Only successful fetch replaces the page; fixed server ordering and shifting offset pages must not become client sorting or snapshot guarantees.

UX-DR23: Detail distinguishes cold read, receipt, invalid ID, identical unknown/inaccessible404, server/offline/read-retry and auth loss. A new route ID immediately removes old receipt facts and preserves the exact request/display ID. No fake empty receipt or ownership explanation is shown.

UX-DR24: Provide keyboard/touch parity and logical Tab order. Focus page headings on routes, stage headings on review/completion, linked summary/first invalid field on invalid submit and relevant field on Edit. Keep read-page focus stable and block hover-only/single-letter-shortcut dependencies. Back/forward/reload/signin may read but never submit/replay a transfer.

UX-DR25: Verify native semantic navigation/main/headings/forms/lists/description lists, associated labels and invalid/help descriptions, text statuses and no obscured focus. Targets: normal text ≥4.5:1, large text/meaningful control boundaries ≥3:1, 44px controls. Validate rendered combinations rather than claiming accessibility certification from token checks alone.

UX-DR26: Announce page/stage, load completion, errors and transfer outcome once at appropriate urgency, with aria-busy and live status regions. Avoid per-digit balance announcements and ensure pending/disabled actions have readable surrounding status.

UX-DR27: Verify 320 CSS px reflow and 200% zoom with wrapping navigation/actions/names/emails/IDs/maximum money, no clipped information or hidden actions, and no font shrinking to fit exact values. Respect reduced motion; no animation is required for comprehension.

UX-DR28: Display UTC API instants consistently in browser-local date/time, with an explicit timezone/offset on detail. Do not mutate wire timestamps or use floating-point locale conversion for money.

UX-DR29: Use calm, specific microcopy that distinguishes validation/confirmed rejection, confirmed completion, unknown outcome, stale refresh and offline read. Unknown/inaccessible detail has identical “Transaction not found”; invalid signin is generic. No internal SQL/framework text or speculative recipient/balance claims.

UX-DR30: Preserve all four source journeys in acceptance: FE-UJ-1 Amina signup→zero-Wallet confirmation→signin→Dashboard; FE-UJ-2 manual fixture funding outside UI→review→one transfer→receipt; FE-UJ-3 Rahim RECEIVED/Amina SENT for the same ID→private detail; FE-UJ-4 valid reload/expiry→unknown outcome→Wallet/history review without replay. Include failure/recovery paths, not only happy navigation.

UX-DR31: Use approved Dashboard and Send money HTML references to anchor composition/review/unknown specimen; they are sample-data static mocks, not production code or simultaneous runtime states. The other four screens are specified by the spine tables. Specifications override mocks on conflict; four extra previews, external design tools and new visual directions are not prerequisites.

### FR Coverage Map

| Requirement | Epic coverage | User value |
|---|---|---|
| FE-FR-1 | Epic 5 | Register in the browser and receive truthful account/zero-Wallet confirmation. |
| FE-FR-2 | Epic 5 | Sign in and open the authenticated browser experience. |
| FE-FR-3 | Epic 5; Epic 6 extends transfer-specific recovery | Retain valid signin across reload, clear private data safely, and never replay a transfer. |
| FE-FR-4 | Epic 5; Epic 6 consumes refresh after transfer | View current profile and exact USD Wallet balance with honest read/refresh states. |
| FE-FR-5 | Epic 6 | Review and send once, understand confirmed/rejected/unknown outcomes, and recover safely. |
| FE-FR-6 | Epic 6 | Browse participant-relative paginated history. |
| FE-FR-7 | Epic 6 | Read an ownership-protected exact-ID receipt. |
| FE-FR-8 | Epic 5 establishes shared behavior; Epic 6 applies receipt/transfer cases | Navigate and understand validation, authentication, read and transfer feedback. |

Cross-cutting requirements are delivered with their user flows, not left to a separate infrastructure or quality epic:

| Inventory coverage | Epic 5 responsibility | Epic 6 responsibility |
|---|---|---|
| FE-NFR-1..5 | Responsive/accessibility/privacy/precision tests and direct-local account/Wallet delivery. | Apply the same standards to transfers/receipts; complete six-screen smoke and combined-JAR delivery. |
| FE-AR-1..11 | Establish Angular starter, pinned toolchain, ownership/shared contracts, lossless core, approved persistence/bootstrap and scoped authentication. | Reuse those contracts without a second client/session implementation. |
| FE-AR-12..14 | No transfer dispatch is needed for this increment. | Implement minimal attempt-scoped recovery, single dispatch/settlement and exact outcome handling. |
| FE-AR-15..17 | Establish latest-read protection, shared DTO/error contracts and truthful Dashboard states. | Apply them to changing receipt IDs/history pages and transfer responses. |
| FE-AR-18..19 | Unit/HTTP/browser and real-backend evidence for the delivered account/Wallet flows. | Extend evidence to transfers/receipts, late responses, recovery and the full real-backend journey. |
| FE-AR-20..22 | Working development proxy and documented direct-local frontend/backend startup; backend-only commands stay independent. | Finish opt-in combined-JAR packaging, reserved-path security and packaged browser/regression verification. |
| UX-DR1..9 | Establish tokens, responsive geometry, account pages, shell, native actions and fields. | Reuse the same patterns on all remaining screens. |
| UX-DR10; UX-DR18..20 | Complete balance/profile and signup/signin/Dashboard states. | Preserve them when transfer-related reads refresh or authentication ends. |
| UX-DR11..15; UX-DR17; UX-DR21..23 | Not prerequisites for account/Wallet usability. | Complete review/results, history/detail, status, pagination and every transfer/receipt state. |
| UX-DR16; UX-DR24..29 | Establish feedback, keyboard/focus/live-region, contrast/reflow and timestamp conventions for delivered screens. | Apply and verify these conventions across transfer and receipt surfaces. |
| UX-DR30 | FE-UJ-1 and FE-UJ-4's valid reload/expiry behavior. | FE-UJ-2, FE-UJ-3 and FE-UJ-4's unknown-transfer recovery; verify all four completed journeys. |
| UX-DR31 | Dashboard visual reference; specification-driven signup/signin. | Send money visual reference; specification-driven history/detail. |

## Epic List

**Approved:** two frontend epics, numbered 5–6. Both deliver browser-visible user outcomes. Epic 6 builds on Epic 5 and the already completed backend; neither requires a later frontend epic to function. The final six-screen scope is unchanged.

### Epic 5: Access Your Account and Wallet in the Browser

Users can register, sign in, retain valid authentication across reload, view their own profile and exact USD Wallet balance, refresh safely, and sign out locally from a responsive, accessible browser interface.

**FRs covered:** FE-FR-1, FE-FR-2, FE-FR-3, FE-FR-4, and the shared navigation/feedback baseline of FE-FR-8.

**User outcome:** Amina can complete signup → zero-Wallet confirmation → explicit signin → Dashboard, then reload while valid or sign out without another User's data resurfacing. Loading, unavailable and stale values are never presented as confirmed balances.

**Included supporting work:** official Angular foundation and approved tokens; shared API/codec/models before feature consumers; sessionStorage/bootstrap, request generations and restricted bearer attachment; account/Wallet forms, safe errors and read states; relevant unit/browser/real-backend tests and direct-local startup documentation. Shared contracts cover the final product without requiring transfer/receipt pages to exist first.

**Dependency and independence:** uses the completed backend only. A developer can run and explore these delivered flows through the development proxy without Epic 6 or the later combined packaging step. Unbuilt screens must not masquerade as completed features; final navigation remains the approved UX contract.

### Epic 6: Send Money and Verify Private Transaction Receipts

Users can review/send money once, distinguish completion/rejection/unknown outcomes, investigate through their own Wallet/history, and inspect participant-protected receipts with exact amounts and IDs. The complete six-screen experience can also run from the existing Spring Boot JAR.

**FRs covered:** FE-FR-5, FE-FR-6, FE-FR-7, and transfer/receipt-specific FE-FR-3, FE-FR-4 and FE-FR-8 behavior.

**User outcome:** Amina can review a transfer to Rahim, receive a confirmed receipt or honest recovery guidance, and read the same completed transaction as SENT while Rahim sees RECEIVED. A lost response never triggers a replay or becomes a fabricated failed Transaction.

**Included supporting work:** history/detail and bounded pagination; inline send/review/result flow, strict amount validation and attempt-scoped recovery; late-response/unknown/success-with-failed-refresh protection; responsive/accessibility and real-backend journey tests; explicit combined-JAR packaging and direct-deep-link/static/reserved-API security checks, with no Docker or second runtime service.

**Dependency and independence:** builds on Epic 5 and the existing backend only. Receipt access is available before transfer recovery is accepted, within this epic's ordered implementation, so recovery links do not depend on a future epic. Existing backend transfers/fixtures can provide history while browser Send money is being built. No later epic is needed to complete the user journey.

### Grouping and Dependency Check

The approved delivery order is **completed backend → Epic 5 → Epic 6**. There is no cycle or later-epic prerequisite. Tests, safe errors, accessibility and documentation accompany each increment; final combined delivery completes Epic 6 rather than forming a technical-only epic.

The core API/session files are intentionally shared: establish their public contracts once in Epic 5, then extend only the needed recovery/receipt behavior in Epic 6. Transfers and receipts stay together because unknown-outcome recovery needs working receipt access. A separate history epic would either delay safe transfers or require overlapping partial receipt work. Given the already validated PRD/UX/architecture, this two-epic grouping avoids unnecessary fragmentation.

## Epic 5: Access Your Account and Wallet in the Browser

Users can register, sign in, retain valid authentication across reload, view their own profile and exact USD Wallet balance, refresh safely, and sign out locally from a responsive, accessible browser interface.

### Story 5.1: Establish the Runnable Angular Frontend

As a PayNest learner,
I want a runnable Angular + Tailwind foundation connected to the existing backend,
So that I can build the browser features incrementally.

**Acceptance Criteria:**

1. **Given** the existing repository, **when** the frontend is scaffolded, **then** it resides in `frontend/` using the approved Angular CLI baseline: standalone, strict, zoneless, routing, Tailwind, Vitest and SSR disabled—without a nested Git repository.

2. **Given** the documented Node/npm prerequisites, **when** `npm ci`, the production build and single-run unit tests execute, **then** they succeed using the committed lockfile and approved dependency versions.

3. **Given** the application starts, **when** its root component renders, **then** it uses PayNest branding and approved global color, typography, spacing and radius tokens, without fake balances or presenting unfinished features as functional.

4. **Given** the configured backend is running, **when** a development request targets `/api/**`, **then** Angular proxies it to Spring Boot. A protected Wallet request without credentials retains the backend’s JSON 401 response rather than returning SPA HTML.

5. **Given** the frontend is added, **when** existing backend commands run, **then** they remain independent of Node. No backend business logic, schema, API contracts, Docker or runtime infrastructure changes are introduced.

6. **Given** a fresh checkout, **when** the learner follows the updated local instructions, **then** setup, start, build and test commands are reproducible; generated dependencies/build outputs and secrets are not committed.

### Story 5.2: Preserve Exact Money and Identifiers

As a PayNest user,
I want amounts and transaction identifiers preserved exactly,
So that balances, transfers and receipts never silently lose cents or identifier digits.

**Dependencies:** Story 5.1 only.
**Requirements:** FE-NFR-2; FE-AR-6, FE-AR-7 and the numeric-testing portion of FE-AR-18.

**Acceptance Criteria:**

1. **Given** raw JSON response text, **when** the shared numeric codec parses it, **then** `lossless-json` preserves numeric lexemes before validation. Money, identifiers and record counts never pass through floating-point conversion.

2. **Given** valid response money, **when** it is validated, **then** it becomes an exact two-decimal string up to `99999999999999999.99`. Wallet balances permit zero; transfer amounts must be positive. Out-of-range values and nonzero sub-cent precision are rejected without rounding.

3. **Given** an entered transfer amount, **when** it is validated, **then** only positive, ungrouped plain decimals with at most two entered fractional digits are accepted. Inputs such as `25.500`, `1e2`, negative amounts and zero are rejected before normalization.

4. **Given** numeric identifiers or history counts, **when** they are decoded, **then** identifiers become positive decimal strings up to `9223372036854775807`. Counts use a separate nonnegative int64 representation that permits zero.

5. **Given** valid exponent-form response numbers, **when** they are normalized, **then** their exact value is preserved. Exponent and significand processing is bounded before expansion; malformed or unsupported values produce a safe contract failure.

6. **Given** a validated transfer amount and recipient email, **when** the request is serialized, **then** the amount remains an exact JSON numeric token and the email remains a correctly escaped JSON string. Existing API wire types are unchanged.

7. **Given** the numeric helpers, **when** unit tests run using literal raw JSON fixtures, **then** maximum money, unsafe-integer IDs, zero counts, exponent forms, invalid precision and serialization round trips are covered. No feature screens, authentication infrastructure or backend changes are required.

### Story 5.3: Establish the Shared Typed API Client

As a PayNest learner,
I want one typed client for the existing backend APIs,
So that browser features share consistent requests, validated responses and safe errors.

**Dependencies:** Stories 5.1–5.2 and the completed backend.
**Requirements:** FE-AR-4’s API contract, FE-AR-6’s transport boundary, FE-AR-16, FE-AR-17 and relevant FE-AR-18 tests.

**Acceptance Criteria:**

1. **Given** the existing seven endpoints, **when** the shared `PayNestApi` is implemented, **then** it exposes exactly the approved operations: `signup`, `signin`, `currentUser`, `wallet`, `transfer`, `history(page = 0)` and `detail(id)`, using their documented request/response models.

2. **Given** an API method is called, **when** its Observable is subscribed to, **then** it issues one corresponding HTTP request using a relative `/api` URL. Construction and method invocation alone issue no request; the client adds no automatic retries, caching or replay.

3. **Given** a response arrives, **when** it is decoded, **then** success and error bodies enter as text and pass through the shared lossless codec. Only runtime-validated response shapes become typed successes; malformed JSON, missing required fields, invalid values or unexpected success statuses produce contract failures.

4. **Given** the shared readonly models, **when** responses are mapped, **then** they preserve the existing contracts: Wallet has no ID; transfer returns `transaction` plus `balanceAfter`; detail has sender/recipient; history items have counterparty. Money and identifiers retain Story 5.2’s exact representations.

5. **Given** a history page or detail identifier, **when** its request is prepared, **then** history uses fixed size 20 and a validated nonnegative bounded page, while detail preserves the validated identifier without numeric conversion. Pagination responses retain `content`, `page`, `size`, `totalElements`, `totalPages` and `last`, without inventing `first`.

6. **Given** an HTTP, network, contract or local-input failure, **when** the client reports it, **then** it uses the shared `ApiFailure` error channel with its defined kind, optional validated status/code, safe message and optional field errors. It never exposes raw response bodies, SQL, stack traces or credentials.

7. **Given** the client is provided application-wide, **when** it is used or tested, **then** it owns no session, storage or screen state and depends on no future page or SessionService implementation. Authentication attachment and lifecycle coordination remain separate responsibilities.

8. **Given** HTTP tests with literal JSON fixtures, **when** they run, **then** they verify all seven method/path mappings, request serialization, expected success statuses, DTO validation, numeric precision, error mapping and absence of automatic POST retries.

### Story 5.4: Coordinate Sign-in and Protect Bearer Credentials

As a PayNest user,
I want authentication to use my current credentials safely,
So that signing out or changing accounts cannot expose another session’s private data.

**Dependencies:** Stories 5.1–5.3.
**Requirements:** FE-FR-2/3’s session foundation; FE-NFR-3; applicable FE-AR-4, FE-AR-8, FE-AR-11 and FE-AR-18.

**Acceptance Criteria:**

1. **Given** the application’s shared services, **when** authentication is coordinated, **then** one root-scoped `SessionService` owns credentials and exposes readonly session phase, current User and generation signals. Construction performs no HTTP or restoration.

2. **Given** explicit sign-in credentials, **when** `signin()` runs, **then** it owns one subscription to the sign-in request, validates the token response, persists the credential and verifies `/api/users/me`. Its Promise resolves with the trusted User only after verification; token receipt alone does not expose authenticated content.

3. **Given** successful credential persistence, **when** sessionStorage is inspected, **then** the credential record contains only `accessToken`, `expiresAt` and a fresh nonsecret login nonce. Passwords and private response data are never persisted. Storage denial rejects sign-in safely through `ApiFailure`, without silently falling back to memory-only authentication.

4. **Given** a usable credential, **when** an HTTP request is dispatched, **then** a request-time interceptor attaches its bearer token only to the five exact protected same-origin PayNest method/path operations. Auth endpoints, assets, documentation, lookalike paths, unsupported methods and other origins receive no token. Expired credentials are not attached.

5. **Given** sign-out, expiry or a current-session protected 401, **when** the session ends, **then** credentials and private recovery metadata are cleared, the trusted User is removed and generation advances. Local sign-out sends no backend mutation and does not claim to revoke the JWT.

6. **Given** overlapping authentication attempts or requests from an earlier generation, **when** delayed success or failure arrives, **then** it cannot install old credentials, restore the previous User or end a newer session. Every sign-in attempt invalidates eligibility of the previous generation.

7. **Given** invalid sign-in credentials, **when** the public sign-in endpoint returns 401, **then** the result is a generic credential failure—not protected-request expiry handling. Other failures retain the shared safe error contract, without logging passwords or tokens.

8. **Given** service and interceptor tests, **when** they run, **then** they cover successful verification, storage denial, failed verification, scoped headers, expiry, sign-out, protected 401 and late-response races. The learning-only sessionStorage risk is documented.

**Scope boundary:** Reload restoration, lifecycle timers, route guards and visible account screens will be separate stories. This foundation is testable without them.

### Story 5.5: Restore Valid Sessions and Enforce Expiry

As a PayNest user,
I want to remain signed in after reloading while my JWT is valid,
So that I can continue safely without extending my authentication lifetime.

**Dependencies:** Stories 5.1–5.4.
**Requirements:** FE-FR-3; relevant FE-NFR-3/4 and FE-AR-5, FE-AR-9, FE-AR-10, FE-AR-11 and FE-AR-18.

**Acceptance Criteria:**

1. **Given** application startup, **when** dependency construction completes, **then** an explicit initializer invokes `SessionService.restore()`. Constructors remain side-effect-free, and restoration does not create circular dependencies or duplicate session instances.

2. **Given** a valid stored credential, **when** restoration begins, **then** the session enters `restoring` and verifies `/api/users/me` before exposing authenticated content. Successful verification restores the trusted User using the same JWT, expiry and login nonce—without another sign-in request or expiry extension.

3. **Given** missing, malformed or expired stored credentials, **when** restoration runs, **then** no unusable token is sent. Invalid credentials are cleared, the session becomes anonymous and `restore()` resolves with `null`. A current protected 401 also applies the existing session-ending behavior.

4. **Given** a network/server verification failure, **when** restoration cannot complete, **then** its Promise rejects with `ApiFailure`, private content remains hidden and accessible bootstrap feedback offers explicit retry. Storage-access failure is also visible, with no memory-only fallback. Neither failure crashes application initialization or triggers automatic request retries.

5. **Given** an active credential, **when** its expiry arrives or the browser regains focus/visibility, **then** expiry is rechecked and an expired session ends using Story 5.4’s cleanup. Protected dispatch retains its expiry check; browser timer delays cannot extend token validity.

6. **Given** restoration or retry is pending, **when** sign-out, expiry or a newer sign-in invalidates its generation, **then** late results cannot restore the old User or clear newer credentials. Repeated retry activation while pending does not create duplicate verification requests.

7. **Given** documented session behavior, **when** a learner reviews it, **then** it explains origin/tab isolation, separate sign-in across development and packaged origins, browser duplication/restoration limitations and local sign-out without server revocation. Reload performs no business POST or transfer replay.

8. **Given** bootstrap and lifecycle tests, **when** they run, **then** they cover valid reload, missing/malformed/expired credentials, storage denial, verification failure and explicit recovery, protected 401, expiry events and stale-response races.

**Scope boundary:** This story adds session restoration and bootstrap feedback, not another product screen. Account pages and route guards remain separate stories.

### Story 5.6: Sign In Through an Accessible Browser Screen

As a registered PayNest user,
I want to sign in through the browser,
So that I can access my authenticated account.

**Dependencies:** Stories 5.1–5.5; an account created through the existing backend.
**Requirements:** FE-FR-2; navigation portions of FE-FR-3/8; relevant UX-DR5, UX-DR7–9, UX-DR19 and UX-DR24–27.

**Acceptance Criteria:**

1. **Given** an anonymous visitor, **when** `/signin` opens, **then** it displays the approved responsive auth layout with associated email/password labels, password visibility control and Sign in action.

2. **Given** entered credentials, **when** validation runs, **then** email validation matches the backend contract and password is required but preserved exactly. Signup-only password-length restrictions are not applied. Invalid submission identifies and focuses the relevant errors.

3. **Given** valid input, **when** Sign in is activated, **then** the page calls `SessionService.signin()` once. Pending feedback remains visible and repeated clicks or Enter presses cannot create concurrent submissions.

4. **Given** successful credential persistence and current-user verification, **when** sign-in completes, **then** navigation opens `/dashboard`. This story provides a minimal guarded Dashboard landing with trusted User identity and Sign out, without fake balances or apparently functional unfinished features.

5. **Given** failed authentication, **when** feedback appears, **then** unknown email and wrong password both show “Email or password is incorrect.” Network, server, storage and verification failures receive safe, distinct explanations. Passwords clear after submission completion or route departure and are never logged or placed in URLs.

6. **Given** protected Dashboard navigation, **when** the session is anonymous, restoring or ended, **then** private content is withheld. Anonymous/ended sessions go to Sign in; restoration uses existing bootstrap feedback. Expiry/current protected 401 explains “Your session ended. Sign in to continue.”

7. **Given** a signed-in User, **when** Sign out is activated, **then** private content clears and navigation returns to Sign in. Back navigation and late responses cannot restore it. Root/unknown routes resolve to Dashboard when authenticated, otherwise Sign in, without adding another screen.

8. **Given** keyboard use, narrow screens or zoom, **when** the delivered screens are explored, **then** approved tokens, visible focus, 44px controls, heading focus, readable errors and pending announcements remain usable at 320 CSS pixels and 200% zoom.

9. **Given** component/router tests and a browser journey against the existing backend, **when** verification runs, **then** it covers successful sign-in, generic credential rejection, duplicate prevention, password cleanup, valid reload, expiry and sign-out protection.

**Scope boundary:** Full Wallet display, signup and navigation to unbuilt features remain separate work.

### Story 5.7: Register Through an Accessible Browser Screen

As a new PayNest user,
I want to create my account through the browser,
So that I receive a Wallet and can sign in.

**Dependencies:** Stories 5.1–5.6.
**Requirements:** FE-FR-1; relevant FE-FR-8, FE-NFR-1/3/4 and UX-DR5, UX-DR7–9, UX-DR18, UX-DR24–27 and UX-DR30.

**Acceptance Criteria:**

1. **Given** an anonymous visitor, **when** `/signup` opens directly or through the Sign in screen’s create-account link, **then** it displays the approved auth layout with name, email, password and Create account controls. A working Sign in link permits return without submitting.

2. **Given** entered registration details, **when** validation runs, **then** it matches backend Unicode-aware normalization and length rules: trimmed name 2–100 characters, valid normalized email up to 254 and exact password 8–72. Passwords are never trimmed, and the browser does not reject otherwise backend-valid values.

3. **Given** valid input, **when** Create account is activated, **then** exactly one signup request is subscribed to through `PayNestApi`. Visible pending feedback and submission locking prevent duplicate clicks or Enter presses from issuing concurrent requests.

4. **Given** a validated 201 response confirming the account and zero USD Wallet, **when** registration completes, **then** confirmation replaces the form: “Account created. Your Wallet starts at 0.00 USD. Sign in to continue.” The password clears, confirmation receives appropriate focus/announcement and Sign in opens the existing screen. Signup does not authenticate or create credentials locally.

5. **Given** validation errors or duplicate-email 409, **when** feedback appears, **then** relevant field errors are associated with their controls and duplicate email is explained clearly. A safe summary remains when errors cannot map to fields. Invalid submission focuses the summary or first invalid field.

6. **Given** server failure, offline operation or an ambiguous response, **when** signup cannot be confirmed, **then** feedback does not falsely claim the account was created or absent. Response-loss recovery offers Sign in before a deliberate new signup attempt; no automatic POST retry or replay occurs.

7. **Given** keyboard, narrow-screen or zoom use, **when** the form and confirmation are explored, **then** labels, password show/hide, visible focus, readable errors, pending announcements and 44px controls follow the approved UX. Content reflows at 320 CSS pixels and 200% zoom. Passwords clear after request completion/navigation and never enter storage, URLs or logs.

8. **Given** component tests and a real-backend browser journey using the guarded test database, **when** verification runs, **then** it covers validation boundaries, Unicode parity, duplicate email, duplicate prevention, confirmed signup, ambiguous recovery and explicit subsequent sign-in. No funding API or backend changes are introduced.

### Story 5.8: View and Refresh Your Wallet Dashboard

As an authenticated PayNest user,
I want to see my profile and exact Wallet balance,
So that I can understand my account’s current state without mistaking unavailable data for zero.

**Dependencies:** Stories 5.1–5.7.
**Requirements:** FE-FR-4; relevant FE-FR-3/8, FE-NFR-1–5, FE-AR-15 and UX-DR2, UX-DR4–6, UX-DR10, UX-DR20 and UX-DR24–31.

**Acceptance Criteria:**

1. **Given** authenticated Dashboard entry, **when** data loads, **then** `/api/users/me` and `/api/wallet` are read independently through the shared client. Requests use authenticated identity, without selectable User or Wallet IDs.

2. **Given** a successful Wallet response, **when** the balance renders, **then** it shows the full exact amount with two decimals and USD using the approved money typography. `0.00 USD` appears only after a confirmed zero response; no floating-point formatting or client-calculated balance is used.

3. **Given** loading or failed reads, **when** Dashboard renders, **then** profile and Wallet regions have independent loading, unavailable, error and explicit retry states. One region’s non-authentication failure does not hide the other’s successful data. Missing data never becomes a fabricated zero or profile.

4. **Given** previously loaded data, **when** Refresh or a read retry starts, **then** affected previous values are clearly marked stale until a successful response replaces them. Failure retains that explanation and a safe recovery action rather than presenting old values as newly current.

5. **Given** overlapping reads, **when** responses arrive out of order, **then** only the latest matching request for each region may update its data, errors or loading state. Request eligibility includes the current session generation; older responses cannot overwrite newer results.

6. **Given** sign-out, expiry, identity change or current protected 401, **when** the session changes, **then** private Dashboard data clears immediately and existing authentication navigation applies. Late responses and browser Back cannot restore the previous User’s facts.

7. **Given** desktop or narrow-screen use, **when** Dashboard renders, **then** it follows the approved reference: balance/Refresh before profile, 224px desktop sidebar, bounded main content and visible wrapping mobile navigation. Dashboard has an active-state indicator and Sign out works. Unbuilt destinations are not presented as working features.

8. **Given** keyboard use, 320px reflow or 200% zoom, **when** Dashboard is explored, **then** full amounts, names and emails remain readable without truncation or shrinking. Heading focus, stable refresh focus, loading announcements, visible focus, contrast and 44px controls follow the approved UX.

9. **Given** automated tests and the direct-local runbook, **when** verification runs, **then** it covers independent failures, zero/max balances, stale refresh, out-of-order responses and session cleanup. A guarded real-backend journey verifies signup → explicit sign-in → confirmed zero-Wallet Dashboard → valid reload → sign-out. Funding remains manual/test-only, with no new endpoint or infrastructure.

**Scope boundary:** Send money and Transactions destinations become functional in Epic 6; this story does not implement those screens.

### Epic 5 Planning Coverage Check

Eight stories (5.1–5.8) are individually approved for planning. This is a requirements/dependency check, not implementation or test-execution evidence. The user confirmed Epic 5 story planning is complete and authorized proceeding to Epic 6 story drafting.

| Epic 5 functional scope | Approved stories |
|---|---|
| FE-FR-1: browser signup and zero-Wallet confirmation | 5.3, 5.7 |
| FE-FR-2: browser sign-in | 5.3, 5.4, 5.6 |
| FE-FR-3: valid reload, expiry, sign-out and prior-session cleanup | 5.4–5.6, 5.8 |
| FE-FR-4: own profile, exact Wallet balance and honest refresh | 5.2, 5.3, 5.8 |
| FE-FR-8: account/Wallet navigation and shared safe feedback | 5.3–5.8 |

Foundation, precision, privacy, accessibility, relevant automated/real-backend verification and direct-local instructions accompany these stories. Dependencies use only the completed backend and earlier frontend stories. Transfer-specific recovery, receipt/history behavior, final six-screen navigation and combined-JAR delivery remain Epic 6 responsibilities under the approved coverage map. No product implementation is authorized by this planning approval.

## Epic 6: Send Money and Verify Private Transaction Receipts

Users can review/send money once, distinguish completion/rejection/unknown outcomes, investigate through their own Wallet/history, and inspect participant-protected receipts with exact amounts and IDs. The complete six-screen experience can also run from the existing Spring Boot JAR.

**FRs covered:** FE-FR-5, FE-FR-6, FE-FR-7, and transfer/receipt-specific FE-FR-3, FE-FR-4 and FE-FR-8 behavior.

**Supporting requirements:** Apply FE-NFR-1–5, shared client/session/precision and safe-read contracts, FE-AR-12–14 transfer recovery, FE-AR-18–22 verification/delivery, and the approved transfer/history/detail UX requirements. Stories remain sequential: history and detail are available before transfer recovery needs them. Existing backend fixtures support receipt development without a browser transfer feature. Only approved stories will be appended below.

### Story 6.1: Browse Your Transaction History

As an authenticated PayNest user,
I want to browse my sent and received transactions,
So that I can review completed money movements involving my Wallet.

**Dependencies:** Epic 5 and the completed backend.
**Requirements:** FE-FR-6; relevant FE-FR-3/8, FE-AR-15/16 and UX-DR13, UX-DR15–17, UX-DR22 and UX-DR24–30.

**Acceptance Criteria:**

1. **Given** authenticated navigation, **when** `/transactions` opens, **then** it requests page 0 with size 20 through the shared client. Transactions becomes a working, visibly active shell destination; no selectable owner ID is supplied.

2. **Given** a populated response, **when** rows render, **then** each shows SENT/RECEIVED, counterparty name/email, exact two-decimal USD amount, COMPLETED and browser-local date/time. Identifiers remain exact strings. Only server ordering applies—no client sorting, filtering or fabricated rows.

3. **Given** initial loading, successful empty data or a failed read, **when** the page renders, **then** these states are distinct. Empty-history copy appears only after successful retrieval; failures provide safe feedback and explicit read retry.

4. **Given** pagination metadata, **when** Previous or Next is activated, **then** navigation stays within available pages, uses fixed size 20 and displays the page number as backend page plus one. Controls communicate pending/disabled states; no page-size selector is added.

5. **Given** an already loaded page, **when** pagination or Refresh fails, **then** the last successful rows and page number remain, clearly marked as not updated. Only successful retrieval replaces them. Refresh requests page 0 and no read automatically retries.

6. **Given** overlapping requests, **when** responses arrive out of order, **then** only the latest matching session generation and page/request key may update rows, errors or loading state.

7. **Given** sign-out, expiry, identity change or current protected 401, **when** authentication ends or changes, **then** history clears immediately and existing sign-in handling applies. Late responses cannot restore previous-session rows.

8. **Given** desktop, narrow-screen, keyboard or zoom use, **when** history is explored, **then** one semantic list changes from grid-aligned to stacked rows without dropping facts. Full values wrap; pagination focus remains stable and successful page changes are announced.

9. **Given** automated tests and guarded real-backend fixtures, **when** verification runs, **then** it covers empty/populated history, participant-relative directions, precision, pagination bounds, failed navigation, stale responses and session cleanup. Fixtures use existing backend transfers, not a new funding API.

**Scope boundary:** Receipt links are added with the next detail-screen story, so this increment has no dependency on an unbuilt screen.

### Story 6.2: View a Private Transaction Receipt

As an authenticated transaction participant,
I want to open a completed transaction’s receipt,
So that I can verify its amount, participants and recorded time.

**Dependencies:** Epic 5 and Story 6.1.
**Requirements:** FE-FR-7; receipt-link portion of FE-FR-6; relevant FE-FR-3/8, FE-AR-15/16 and UX-DR13–16, UX-DR23–30.

**Acceptance Criteria:**

1. **Given** a history row or direct `/transactions/:id` navigation, **when** a receipt opens, **then** the guarded route requests the exact identifier through the shared client. History rows now provide accessible receipt links, and Transactions remains the active navigation destination.

2. **Given** a valid detail response, **when** it renders, **then** a semantic description list shows the full identifier, viewer-relative SENT/RECEIVED direction, sender and recipient names/emails, exact two-decimal amount, USD, COMPLETED and browser-local date/time with explicit timezone or offset. History-only counterparty data cannot substitute for detail participants.

3. **Given** a route identifier, **when** validation runs, **then** it must satisfy the approved positive BIGINT string contract without numeric coercion. Invalid identifiers receive safe feedback without an API request; valid identifiers retain every digit through navigation, request and display.

4. **Given** a receipt is visible, **when** the route changes to another identifier, **then** old receipt facts clear immediately and the new request shows loading. Only the latest matching identifier/request and session generation may update data, errors or loading state.

5. **Given** an unknown or inaccessible transaction, **when** the backend returns its ownership-safe 404, **then** both cases show the identical “Transaction not found.” presentation. The screen reveals neither ownership nor whether another User’s transaction exists.

6. **Given** network, server or response-contract failure, **when** detail cannot load, **then** safe feedback offers explicit read retry and Back to Transactions. Loading/error never becomes an empty or fabricated receipt. Sign-out, expiry, identity change or current protected 401 clears private facts and applies existing session handling.

7. **Given** keyboard, narrow-screen or zoom use, **when** the receipt is explored, **then** the approved maximum-640px layout stacks terms above values on phones. Full identifiers, amounts and participant details wrap without truncation; heading focus, loading/error announcements and visible link focus follow the approved UX. Back navigation never submits a transfer.

8. **Given** automated tests and guarded real-backend fixtures, **when** verification runs, **then** it covers exact large identifiers, invalid IDs, route-change races, read recovery and session cleanup. Both participants can open the same receipt with opposite directions; a third User and a missing transaction receive the same not-found presentation.

### Story 6.3: Protect Transfer Attempts and Recovery State

As a PayNest user,
I want uncertain transfer attempts remembered without being replayed,
So that navigation or reload cannot accidentally send money again.

**Dependencies:** Epic 5 and Stories 6.1–6.2.
**Requirements:** FE-FR-5’s coordination/recovery foundation; relevant FE-FR-3, FE-AR-12–14 and FE-AR-18.

**Acceptance Criteria:**

1. **Given** the existing `SessionService`, **when** transfer coordination is added, **then** it exposes the approved `beginTransfer`, `settleTransfer` and `acknowledgeUnknown` operations with readonly `transferInFlight` and `recoveryRequired` signals. No additional domain entity or generic state framework is introduced.

2. **Given** an authenticated, unexpired session, **when** an attempt begins, **then** a cross-route guard permits only one active attempt and returns a unique ticket only after its recovery marker is stored successfully. Storage failure prevents dispatch; unresolved recovery requires deliberate acknowledgement before fresh intent.

3. **Given** a stored marker, **when** its contents are inspected, **then** it contains only the login nonce, local attempt key and `mayHaveSubmitted` boolean. It contains no amount, recipient, token copy, draft, receipt or replayable request. The attempt key is not sent as a server idempotency key.

4. **Given** an observed transfer result, **when** the shared outcome classifier runs, **then** it applies the approved mapping:

   | Evidence | Local outcome |
   |---|---|
   | Fully validated 201 response with COMPLETED | Completed |
   | Valid ApiError: 400/VALIDATION_ERROR; 404/USER_NOT_FOUND or WALLET_NOT_FOUND; 409/CONFLICT; 422/SELF_TRANSFER_NOT_ALLOWED or INSUFFICIENT_BALANCE | Rejected |
   | Valid ApiError: 401/AUTHENTICATION_FAILED or INVALID_TOKEN; 403/ACCESS_DENIED | Rejected; current protected 401 also ends authentication |
   | Any 5xx, unmapped pair, malformed/unexpected response, network failure, timeout or cancellation after dispatch | Unknown |

   These outcomes are client coordination states—not additional persisted Transaction statuses.

5. **Given** an active ticket, **when** it settles, **then** completed/rejected clears its marker and unknown retains the warning. Settlement releases the in-flight guard once and is idempotent. An older ticket or session generation cannot clear a newer attempt’s marker.

6. **Given** a matching marker during reload or return, **when** recovery state is restored, **then** it becomes an unknown-outcome warning—not a restored command. Another login cannot inherit it. Sign-out clears both storage entries; expiry/401 clears private metadata while supporting a generic explanation that prior submissions are never resent.

7. **Given** unknown recovery and no active attempt, **when** the User explicitly acknowledges fresh-transfer intent, **then** acknowledgement clears recovery without issuing any HTTP request or restoring previous input. Wallet/history reads cannot automatically prove which lost request committed.

8. **Given** focused coordination tests, **when** they run, **then** they cover storage denial, duplicate begin, marker-before-dispatch, classification boundaries, reload recovery, repeated settlement, old-ticket/new-attempt races and authentication changes.

**Scope boundary:** This story implements testable coordination and classification only. The Send money screen will consume these safeguards in the next story.

### Story 6.4: Review and Send Money Safely

As an authenticated PayNest user,
I want to review and explicitly confirm a transfer,
So that I can send money once and understand its confirmed or uncertain outcome.

**Dependencies:** Epic 5 and Stories 6.1–6.3.
**Requirements:** FE-FR-5; relevant FE-FR-3/4/6/8, FE-AR-12–15 and UX-DR11–12, UX-DR21 and UX-DR24–31.

**Acceptance Criteria:**

1. **Given** authenticated navigation, **when** `/send` opens, **then** Send money becomes a working, active shell destination. Entry, review and result remain inline on this route, following the approved single-column design.

2. **Given** recipient email and amount input, **when** validation runs, **then** backend-compatible email rules and Story 5.2’s exact amount rules apply. Zero, negative, over-maximum, exponent/grouped input and excess fractional digits—including `25.500`—cannot advance. Recipient existence, self-transfer, funds and concurrency remain backend decisions.

3. **Given** valid input, **when** Review is activated, **then** the intended recipient email and normalized two-decimal USD amount appear without issuing a POST. Edit returns to the fields with appropriate focus. One Enter event cannot both enter review and confirm.

4. **Given** explicit final confirmation, **when** submission starts, **then** Story 6.3’s ticket and stored marker precede exactly one transfer subscription. Pending locks edits and repeated confirmation across route changes. Detected offline state or marker-storage failure before dispatch prevents submission; nothing is queued or automatically retried.

5. **Given** a fully validated 201/COMPLETED response, **when** completion renders, **then** it shows the returned receipt, exact identifier, counterparty, amount and `balanceAfter`, with a working receipt link. `balanceAfter` is labeled as the balance at transfer time—not a calculated current balance. Wallet reads refresh and the next history entry reloads page 0; refresh failure cannot relabel the transfer as failed.

6. **Given** a known rejection under Story 6.3’s classifier, **when** feedback appears, **then** validation, missing recipient/Wallet, self-transfer, insufficient funds and conflict receive safe, actionable explanations. Relevant field errors are associated with inputs; correction requires deliberate review and confirmation again.

7. **Given** an unknown outcome or restored recovery marker, **when** recovery renders, **then** it explains that completion cannot be confirmed and offers working Dashboard/Transactions navigation. There is no Retry transfer action. Only explicit fresh intent with no attempt in flight opens a new blank entry; matching history is not presented as proof identifying the lost request.

8. **Given** navigation, reload, browser Back or authentication changes, **when** an attempt is interrupted or returns late, **then** Story 6.3’s settlement/recovery policy applies. No transfer is replayed, private facts cannot cross sessions and expiry/401 provides the generic no-replay explanation without retaining private drafts.

9. **Given** keyboard, narrow-screen or zoom use, **when** stages and outcomes are explored, **then** full values wrap, review/completion headings receive focus, Edit restores field focus and errors/outcomes are announced appropriately. Approved contrast, visible focus and 44px controls apply; a transient toast is never the only receipt.

10. **Given** automated tests and guarded real-backend fixtures with manual/test-only funding, **when** verification runs, **then** it covers successful transfer, business rejections, exact amounts, duplicate prevention, storage failure, unknown recovery across reload/navigation, session races and confirmed success with failed refresh. Automatic transfer/test retries remain disabled.

### Story 6.5: Run the Frontend and Backend from One JAR

As a PayNest learner,
I want to build and run the complete application as one Spring Boot artifact,
So that I can explore it without a separate frontend runtime or weakened API security.

**Dependencies:** Epic 5 and Stories 6.1–6.4.
**Requirements:** FE-NFR-5; relevant FE-NFR-3/4 and FE-AR-20–22.

**Acceptance Criteria:**

1. **Given** the documented frontend prerequisites, **when** explicit combined packaging runs, **then** it uses installed Node/npm, `npm ci` and the production frontend build to create the existing executable Spring Boot JAR. Missing prerequisites or failed frontend builds produce a clear failure rather than a falsely successful combined artifact.

2. **Given** generated browser output, **when** it is packaged, **then** the index resides at classpath `/static/index.html`, with remaining resources under `/static/ui-assets/`. Base href is `/`; script, stylesheet, lazy-loaded chunk and public-asset URLs resolve correctly. Generated assets are build outputs, not committed Java source resources.

3. **Given** the combined JAR and existing PostgreSQL configuration, **when** it starts, **then** one Spring Boot process serves both UI and APIs on the same origin. No Angular development server, Node runtime, Docker or additional deployment service is required.

4. **Given** browser HTML navigation, **when** a user opens or reloads any of the six UI routes, **then** the application loads and applies its existing session guards. Root/unknown UI routes retain their authenticated/anonymous redirects without adding another screen.

5. **Given** requests within `/api`, `/v3`, `/swagger-ui`, `/actuator`, `/error` or `/ui-assets`, **when** routing occurs, **then** the entire reserved namespace is excluded from SPA fallback. Missing assets never return the application index; existing handlers and rejections remain authoritative.

6. **Given** protected APIs, unknown API paths or unsupported methods, **when** requests include any Accept header—including `text/html`—**then** existing authorization and JSON error behavior remain unchanged. No blanket public `/**` matcher, broadened API permission or required CORS expansion is introduced.

7. **Given** ordinary backend development commands, **when** builds and tests run without opting into combined packaging, **then** they remain independent of Node/npm. The Angular development proxy continues to work separately.

8. **Given** integration checks against the actual packaged JAR, **when** they run, **then** they verify asset loading, direct deep links, unknown UI navigation, reserved-path exclusions, missing assets and API security regressions. Tests do not accidentally rely on an Angular development server.

9. **Given** a fresh checkout, **when** the learner follows the updated runbook, **then** direct-local development and combined build/start/verification commands are reproducible. Backend secrets remain external, frontend configuration is identified as public and no automatic toolchain installer or new infrastructure is added.

### Story 6.6: Verify the Complete Browser Experience and Handoff

As a PayNest learner,
I want reproducible evidence that the complete browser experience works,
So that I can confidently run, explore and extend the finished MVP.

**Dependencies:** Epic 5 and Stories 6.1–6.5.
**Requirements:** FE-NFR-1–5, FE-AR-18–22 and UX-DR24–31; integrated verification of FE-FR-1–8.

**Acceptance Criteria:**

1. **Given** the existing feature tests, **when** final verification is assembled, **then** it reuses them and adds only missing cross-screen checks. Playwright Chromium journeys are runnable against both the development proxy and combined JAR, without introducing another test framework or runtime service.

2. **Given** explicitly configured fixtures, **when** real-backend smoke tests run, **then** they use the guarded developer-managed `paynest_test` database with manual/test-only funding. Fixture setup refuses an unapproved database target, never resets normal development data and supports deliberate repeatable reruns. Missing prerequisites are reported as blocked—not passed.

3. **Given** two registered participants, **when** the integrated happy-path journey runs, **then** it verifies signup without auto-login, explicit sign-in, confirmed zero balance, fixture-funded transfer review, one confirmation and a completed receipt. Sender and recipient see the same exact transaction ID with opposite directions, and a third User cannot access its detail.

4. **Given** controlled response loss after transfer dispatch, **when** navigation, reload or reauthentication follows, **then** tests demonstrate unknown-outcome guidance and no POST replay. Valid reload retains the existing expiry; expiry/sign-out/protected 401 clears private data. Confirmed completion remains completed when subsequent refresh fails. Automatic transfer/test retries remain disabled.

5. **Given** boundary and failure scenarios, **when** focused HTTP fixtures and existing unit tests run, **then** they verify exact maximum money/BIGINT values, invalid fractional precision, malformed responses, pagination bounds, storage denial and late-response races. Mocked edge tests remain clearly distinguished from real-backend integration evidence.

6. **Given** all six screens, **when** keyboard, narrow-screen and zoom checks run, **then** labels, heading/stage focus, announcements, full-value wrapping, navigation and recovery actions meet the approved UX targets. Verify 320px reflow, 200% zoom, contrast and 44px controls; compare Dashboard/Send money with approved references without requiring additional mockups.

7. **Given** packaged delivery, **when** regression checks run, **then** Story 6.5’s asset/deep-link/reserved-path protections and the unchanged seven-operation OpenAPI contract are verified alongside existing backend tests. No new entities, product endpoints, funding UI or infrastructure appear.

8. **Given** final handoff documentation, **when** the learner follows it, **then** setup, fixture funding, development, combined packaging and verification commands are reproducible. Record results and remaining limitations without exposing passwords, JWTs or secrets in logs or shared evidence. Unexecuted checks are identified explicitly rather than claimed successful.

**Scope boundary:** This completes integration evidence and handoff; it adds no product features.

### Epic 6 Planning Coverage Check

Six stories (6.1–6.6) are individually approved for planning. They deliver history, detail, attempt/recovery coordination, Send money, combined-JAR delivery and integrated verification. This is planning coverage, not evidence that features have been implemented or tests executed. The user selected Continue from the final story-generation menu, confirming progression to formal final validation.

| Epic 6 functional scope | Approved stories |
|---|---|
| FE-FR-5: review, explicit transfer, completion/rejection/unknown recovery | 6.3, 6.4; integrated verification in 6.6 |
| FE-FR-6: participant-relative history, pagination and receipt links | 6.1, 6.2; post-transfer refresh in 6.4 |
| FE-FR-7: ownership-protected exact-ID detail | 6.2; integrated verification in 6.6 |
| FE-FR-3: transfer-specific reload/session cleanup and no replay | 6.3, 6.4, 6.6 |
| FE-FR-4: post-transfer Wallet refresh without false failure | 6.4, 6.6 |
| FE-FR-8: transfer/receipt navigation, safe feedback and recovery | 6.1–6.4, 6.6 |

### Cross-Cutting Story Traceability

The inventory and source contracts remain binding on their mapped stories; this derived traceability does not change approved acceptance criteria or add scope. The complete planned coverage is eight functional requirements, five non-functional requirements, twenty-two architecture requirements and thirty-one UX requirements. Formal final validation passed and the user confirmed workflow completion.

| Non-functional / architecture requirement | Story coverage |
|---|---|
| FE-NFR-1 | 5.6–5.8, 6.1, 6.2, 6.4, 6.6 |
| FE-NFR-2 | 5.2, 5.3, 5.8, 6.1, 6.2, 6.4, 6.6 |
| FE-NFR-3 | 5.1, 5.3–5.7, 6.3–6.6 |
| FE-NFR-4 | Feature tests throughout; integrated verification in 6.6 |
| FE-NFR-5 | 5.1, 5.8, 6.5, 6.6 |
| FE-AR-1 | 5.1, 6.5, 6.6; unchanged completed backend remains authoritative |
| FE-AR-2 | Foundation/core ownership in 5.1, 5.3, 5.4; feature-owned pages in 5.6–5.8, 6.1, 6.2, 6.4 |
| FE-AR-3 | 5.1; reproducible builds in 6.5 |
| FE-AR-4 | 5.3, 5.4; transfer coordination extended in 6.3 |
| FE-AR-5 | 5.4, 5.5 |
| FE-AR-6, FE-AR-7 | 5.2, 5.3; consumed and verified by feature stories |
| FE-AR-8 | 5.4, 6.3 |
| FE-AR-9, FE-AR-10 | 5.5, 5.6; packaged-origin instructions in 6.5 |
| FE-AR-11 | 5.4–5.6; private-state cleanup in 5.8, 6.1–6.4 |
| FE-AR-12, FE-AR-13, FE-AR-14 | 6.3, 6.4, 6.6 |
| FE-AR-15 | 5.8, 6.1, 6.2; integrated races in 6.6 |
| FE-AR-16, FE-AR-17 | 5.3; shared contracts consumed by feature stories |
| FE-AR-18 | Focused tests in 5.2–5.8 and 6.1–6.4; integrated verification in 6.6 |
| FE-AR-19 | Real-backend/browser checks in feature stories; complete journeys in 6.6 |
| FE-AR-20 | 5.1, 6.5 |
| FE-AR-21 | 6.5, 6.6 |
| FE-AR-22 | 5.1, 5.8, 6.5, 6.6 |

| UX requirement | Story coverage |
|---|---|
| UX-DR1, UX-DR2, UX-DR3 | 5.1 establishes tokens; 5.6–5.8, 6.1, 6.2, 6.4 apply them; 6.6 verifies |
| UX-DR4 | 5.8 shell; 6.1, 6.2, 6.4 extend it; 6.6 verifies |
| UX-DR5 | 5.6–5.8, 6.1, 6.2, 6.4; redirects also verified in 6.5 |
| UX-DR6 | 5.6, 5.8; Transactions/detail in 6.1, 6.2; Send money in 6.4 |
| UX-DR7, UX-DR8, UX-DR9 | 5.6, 5.7; applicable action/field behavior reused in 6.1, 6.2, 6.4 |
| UX-DR10 | 5.8; post-transfer consistency in 6.4 |
| UX-DR11, UX-DR12 | 6.4 using 6.3 coordination |
| UX-DR13 | 6.1 list plus 6.2 receipt links |
| UX-DR14 | 6.2; transfer-result receipt facts in 6.4 |
| UX-DR15 | 6.1, 6.2, 6.4 |
| UX-DR16 | 5.3 safe failures; 5.6–5.8, 6.1, 6.2, 6.4 feedback |
| UX-DR17 | 6.1 |
| UX-DR18 | 5.7 |
| UX-DR19 | 5.6 with 5.4, 5.5 session behavior |
| UX-DR20 | 5.8 |
| UX-DR21 | 6.4 with 5.2 numeric rules and 6.3 recovery |
| UX-DR22 | 6.1; post-transfer page-0 behavior in 6.4 |
| UX-DR23 | 6.2 |
| UX-DR24, UX-DR25, UX-DR26, UX-DR27 | Applicable feature criteria in 5.6–5.8, 6.1, 6.2, 6.4; cross-screen verification in 6.6 |
| UX-DR28 | 6.1 history, 6.2 detail; cross-screen verification in 6.6 |
| UX-DR29 | 5.6–5.8, 6.1, 6.2, 6.4; verification in 6.6 |
| UX-DR30 | FE-UJ-1 in 5.7, 5.8; FE-UJ-2 in 6.4; FE-UJ-3 in 6.1, 6.2; FE-UJ-4 in 5.5, 6.3, 6.4; integrated journeys in 6.6 |
| UX-DR31 | 5.8 Dashboard reference, 6.4 Send money reference; comparison in 6.6 |

**Dependency and scope check:** completed backend → 5.1–5.8 → 6.1–6.6. Each story can be completed using earlier work only. Temporary unavailable destinations become active in their owning story; no earlier story requires a later screen to function. Fourteen approved stories cover the same six screens, seven product API operations and three persisted domain entities. There is no funding endpoint, Docker, microservice infrastructure, new Transaction lifecycle or additional product scope. No implementation or test execution has occurred as part of this planning workflow.

## Final Validation

**Result:** passed, zero open planning checks. **Workflow state:** final and complete, following the user's explicit completion confirmation. This validates the implementation plan, not implemented application behavior.

| Sequential validation check | Result and evidence |
|---|---|
| 1. Functional and supporting requirements coverage | Pass. FE-FR-1–8 are addressed by the account/Wallet and transfer/receipt acceptance criteria and functional coverage tables. FE-NFR-1–5, FE-AR-1–22 and UX-DR1–31 all have story mappings; the four source journeys are verified in 6.6. |
| 2. Architecture implementation alignment | Pass. Story 5.1 establishes the approved official Angular CLI starter before consumers. It is the first frontend story, not a reopening or renaming of completed backend Story 1.1. No frontend story creates a database table/entity or changes the seven business API contracts. Lossless numeric handling precedes HTTP consumers; authentication and recovery coordination precede their screens. |
| 3. Story quality | Pass. Fourteen correctly numbered stories contain complete As a / I want / So that statements and 115 numbered Given/When/Then criteria. Foundation stories address explicit enabling architecture/non-functional requirements; feature stories reference their functional requirements. Each scope is bounded around existing backend capabilities and earlier foundations, with tests included rather than deferred entirely to the last story. |
| 4. Epic structure and shared-file churn | Pass. Epic 5 delivers usable account/Wallet flows independently of Epic 6. Epic 6 delivers transfers and receipts plus final packaging. Shared API/session ownership is established once; extending SessionService with transfer recovery and incrementally enabling routes is justified by separate account/Wallet and transfer/receipt feedback milestones. The approved two-epic grouping avoids repeatedly creating competing core services or fragmented history/transfer epics. |
| 5. Epic and story dependencies | Pass. The ordered dependency chain is completed backend → Epic 5 → Epic 6. Declared story dependencies refer only to prior work; no forward or missing dependency was found. Sign-in has a minimal usable protected landing before signup links to it. History is independently usable before detail adds receipt links; both exist before Send money recovery uses them. Unbuilt destinations are not presented as functional. |
| 6. Document completion and scope consistency | Pass. No template placeholders remain. Approved PRD, architecture and UX contracts are preserved: six screens, one Spring Boot deployment/database, exactly three persisted entities, numeric wire types, COMPLETED-only records, no public funding API and no Docker/microservice infrastructure. Backend epics and the confirmed requirements inventory remain unchanged. |

**Readiness limits:** No Angular scaffold, dependencies, backend change, database operation, build or application test was performed during this planning workflow. Runtime correctness and browser accessibility must be demonstrated during the corresponding implementation stories, including the real-backend and packaged-JAR checks. SessionStorage retains its explicitly accepted learning-only XSS exposure; local sign-out is not server revocation. These documented source constraints are not new product requirements or open planning blockers.

**Completion confirmation:** The user selected `[C] Complete Workflow` after the final-validation result. All four planning steps are complete and this document is final. No sprint plan or implementation is authorized by that confirmation.
