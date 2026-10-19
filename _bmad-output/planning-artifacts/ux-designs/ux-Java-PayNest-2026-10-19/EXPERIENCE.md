---
title: 'PayNest Frontend Experience Design'
status: final
created: 2026-10-19
updated: 2026-10-19
sources:
  - ../../prds/prd-Java-PayNest-2026-10-19/prd.md
  - ../../prds/prd-Java-PayNest-2026-10-19/addendum.md
visual_identity: DESIGN.md
---

# PayNest experience spine

## Foundation

Responsive browser UX for the existing PayNest backend, using user-selected Angular and Tailwind CSS. [DESIGN.md](DESIGN.md) is the visual identity contract. Six screens use the existing seven product API operations; this document authorizes no implementation. Source requirement names below preserve the frontend PRD's FE identifiers.

Approved interaction direction: same-route entry/review/result sequence, visible small-screen navigation, browser-local timestamp presentation, and inline feedback. The user confirmed the delegated “Choose for me” design. Palette/layout choices are recorded in DESIGN.md rather than repeated here.

The backend remains authoritative for identity, ownership, validation, balance arithmetic and transfer completion. A Wallet has one USD balance; its API does not expose a Wallet ID. A Transaction is an immutable completed receipt. Unknown outcome is a local recovery state, never a Transaction lifecycle. No funding UI, additional APIs, fees, recipient directory, search/filter/sort controls, or new account capabilities are introduced. Manual demonstration funding remains outside this UI. No Docker work is included.

## Information Architecture

| Screen / route | Entry and exit | Data/action contract | Journeys |
|---|---|---|---|
| Sign up `/signup` | Sign-in link or direct public route; successful signup offers Sign in | `POST /api/auth/signup` | FE-UJ-1 |
| Sign in `/signin` | Public route, guard, expiry/401; success → Dashboard | `POST /api/auth/signin` | FE-UJ-1, FE-UJ-3, FE-UJ-4 |
| Dashboard `/dashboard` | Sign-in success or app navigation; links to Send money/Transactions | `GET /api/users/me`, `GET /api/wallet` | FE-UJ-1, FE-UJ-2, FE-UJ-4 |
| Send money `/send` | App navigation; edit/review/result stay on route; receipt links to detail | `POST /api/wallet/transfers` | FE-UJ-2, FE-UJ-4 |
| Transaction history `/transactions` | App navigation or detail back link; row → exact ID detail | `GET /api/transactions` | FE-UJ-3, FE-UJ-4 |
| Transaction detail `/transactions/:id` | Selected receipt or protected deep link; Back to Transactions | `GET /api/transactions/{id}` | FE-UJ-2, FE-UJ-3 |

Dashboard, Send money and Transactions form authenticated navigation; Sign out clears local authentication/private state. Detail highlights Transactions as the current destination. Root/unknown UI route resolves to Dashboard when signed in, otherwise Sign in; it is not a seventh screen. Authenticated routes are guarded for navigation convenience; backend authorization still decides access. Direct deep-link asset serving is an architecture integration decision.

Layout references: [Dashboard](mockups/key-dashboard.html) shows the balance/profile/navigation arrangement; [Send money](mockups/key-send-money.html) shows review and a separate unknown-outcome specimen. Controls are illustrative and do not submit requests. The other four screens use the specification tables; optional extra mockups do not block handoff.

## Voice and Tone

Use short, specific explanations. Show user-safe ApiError messages and mapped field errors; do not expose framework/database internals. Text must distinguish a rejected operation from a response whose outcome is unknown.

| Context | Suggested copy |
|---|---|
| Signup confirmed | “Account created. Your Wallet starts at 0.00 USD. Sign in to continue.” |
| Generic invalid credentials | “Email or password is incorrect.” Same wording for unknown email and wrong password. |
| Expiry/protected 401 | “Your session ended. Sign in to continue.” |
| Empty history | “No transactions yet.” |
| Successful transfer | “Transfer completed.” Only after the required confirmed response. |
| Ambiguous transfer | “We couldn't confirm this transfer's outcome. It may have completed. Check your Wallet and Transactions before sending again.” |
| Refresh after success fails | “Transfer completed. We couldn't refresh your Wallet. Refresh to see the latest balance.” |
| Detail unknown/inaccessible | “Transaction not found.” Identical presentation for both. |
| Offline read | “We couldn't connect. Check your connection, then try again.” |

Backend business-rejection messages should explain the existing missing-recipient, insufficient-funds, self-transfer, or conflict result without guessing at balances, recipient identity or retry safety.

## Component Patterns

| Component | Use | Behavioral rules |
|---|---|---|
| app-shell | All protected routes | Marks active destination with text and `aria-current`; exposes Dashboard, Send money, Transactions and Sign out. Navigation never triggers a Transfer. Sign out clears saved credential/private state and cancels or ignores late responses. |
| auth-form | Sign up / Sign in | Submit once while pending; associated labels and field errors; success behavior is route-specific. Never retain/log passwords after completion or route departure. Sign-in has no signup-only password-length restriction. |
| action-button | Every explicit action | Native button/link semantics. Pending disables repeated activation. A read retry is explicit; transfer confirmation fires one POST for that deliberate action. No hidden double-click/Enter duplicate. |
| form-field | Auth and Send money | Validate without losing entered text; expose invalid/help state programmatically. Name/email follow backend normalization; password is preserved exactly, never trimmed. Password show/hide is keyboard-operable and does not alter its value. |
| balance-panel | Dashboard | Identity and wallet reads can fail independently. Zero is shown only after a successful wallet read. Explicit Refresh marks last-confirmed data stale during retrieval; failed refresh keeps a visible “not refreshed” explanation. |
| transfer-review | Send money | Shows exact intended recipient email and normalized two-decimal display of validated amount. Edit returns to entry; confirm locks edits and sends once. Review itself sends nothing. |
| transfer-result | Send money | Confirmed receipt only for 201 with COMPLETED; show returned balanceAfter as confirmed at transfer time. Rejection permits deliberate correction. Unknown outcome offers Wallet/history navigation and deliberate fresh entry, never Retry transfer. |
| transaction-list | History | One semantic list, each row has an accessible receipt link; display viewer-relative direction, returned counterparty and all required facts. No fabricated pending/failed rows, optimistic rows, client filtering or sorting. |
| receipt-detail | Detail and confirmed transfer result | Show returned facts via labeled description list, exact ID and participants where provided. Detail shows sender/recipient name/email; history counterparty is not substituted for unavailable detail facts. Back navigation never submits. |
| status-label | Confirmed receipts | Announces COMPLETED as text. SENT/RECEIVED is relative to the current authenticated viewer; clear old-viewer data before identity changes. |
| feedback-panel | Field/page/recovery feedback | Safe summary always remains when fieldErrors cannot map to current fields. Alert urgent errors once; ordinary read/loading updates use polite status. Offer only applicable actions. |
| pagination | History | Default `page=0&size=20`; fixed size 20, no size selector. Previous/Next bounded by returned metadata; disable during fetch. Display page number as backend page + 1. Maintain size bounds 1–100 in the request contract. |

## State Patterns

The following rows cover each surface's cold load, valid/empty, focus/input, pending, error, offline and access states where applicable. Focus uses `{colors.focus}` and controls use `{spacing.target-min}` from DESIGN.md; loading/errors never impersonate an empty successful response.

| Surface | States and required response |
|---|---|
| Sign up | Initial empty form, keyboard focus, client-invalid and backend field errors; pending disables concurrent submits. Validate backend-compatible trimmed name 2–100 characters, normalized valid email ≤254, exact password 8–72 with Unicode-aware backend parity. 201 replaces password/form with account + zero-Wallet confirmation and Sign in link, without authenticating. 409 duplicate email is explicit; server failure is a safe summary. Offline or response loss says account creation could not be confirmed and links to sign-in before a deliberate new attempt; no automatic POST replay. Public route has no private-data permission state. |
| Sign in | Empty/focus/invalid-email states; pending submit lock; 200 establishes returned JWT/expiry and navigates to Dashboard. Generic credential failure, server/network failure and expired-session explanation are distinct. No password-length policy beyond matching the signin API; no retained password after submission completion/navigation. No automatic resubmission. Public page contains no previous User's private data. |
| Dashboard | Cold identity/wallet regions show loading text/placeholders. Successful zero is `0.00 USD`; no-wallet-data is “Balance unavailable,” never zero. Identity/wallet errors have explicit retry. Refresh keeps any last-confirmed values labeled not current until success; failure/offline retains stale label. Focus follows heading → Refresh → content/navigation. Unauthenticated/expiry/401 clears private content and goes to Sign in. No business permission tiers are invented. |
| Send money | Initial recipient/amount fields, focused/invalid input, valid review, submitting, confirmed result, confirmed backend rejection and unknown outcome are separate states. Invalid email, nonpositive amount or >2 entered fractional digits (including `25.500`) cannot advance to review; do not round. Entered amount stays exact text up to `99999999999999999.99`. Backend decides recipient existence, self-transfer, funds and conflicts. 409/422/known rejection keeps safe feedback and permits edit, without claiming an unknown outcome. A timeout, lost/ambiguous response or cancellation after dispatch becomes unknown, even if an unrelated read works. Offline before dispatch prevents submission; connectivity loss after dispatch is unknown. Protected 401 clears authentication; any uncertain submitted transfer remains a recovery warning, never a replay. |
| Transaction history | Initial loading, populated list, successful empty list, keyboard focus, page fetch, refresh, failed fetch and offline states. Empty copy appears only on successful empty response. Failed initial load offers read retry; failed page fetch retains current loaded page and clearly says requested page was not loaded. Refresh returns to page 0; after a confirmed new transfer, next history entry reloads page 0. Previous/Next replace list only on success and announce page change. Newest-first means createdAt DESC then id DESC from the server; offset pages may shift as transfers arrive. Protected 401/expiry clears rows and requires sign-in. |
| Transaction detail | Loading exact requested ID, populated receipt, visible link focus, safe invalid-ID error, same not-found state for unknown/inaccessible ID, server/read/offline error with retry. Never leave a prior ID's receipt under a new route. Preserve positive BIGINT ID exactly through route → request → rendered value; no numeric rounding/coercion. No empty placeholder receipt. Protected 401/expiry clears facts and requires sign-in; 404 never reveals ownership. |

Shared pending controls remain keyboard-discoverable through status text; form field errors are announced with their labels. Offline never queues a transfer. No client-computed or optimistic balance or Transaction is displayed. A late response from a previous User/request cannot repopulate a cleared screen.

### Transfer completion and recovery

Entry → valid review → one explicit confirmation → submitting. A 201 response with COMPLETED enters confirmed completion; a confirmed backend rejection enters correction; a response that cannot establish either enters unknown outcome. A malformed/unexpected success response cannot justify “completed” and needs the same cautious recovery treatment.

On confirmed completion, show the returned receipt and balanceAfter, then refresh affected reads without changing the confirmed outcome. If refresh fails, keep “Transfer completed,” distinguish the at-transfer balance from current Wallet data, and offer explicit read refresh. Do not calculate a fresh balance from the draft amount.

On unknown outcome, preserve a minimal local recovery warning across a same-tab reload, scoped to the applicable authentication context. Architecture decides its storage/lifecycle along with token storage; it must contain no password, JWT copy, sensitive draft/receipt payload or replayable POST. It is not a backend Transaction record. A reload during submission recovers to this warning rather than silently resetting to an apparently safe blank form. Wallet/history review can aid investigation but a similar receipt does not prove which request committed. “Start a new transfer” is an explicit fresh action after this warning; it is never automatic retry. Sign-out removes private recovery state; signing back in never replays a request and should still explain that prior submissions are not resent.

## Interaction Primitives

Keyboard and touch have equivalent access. Native Tab/Shift+Tab follows reading order; Enter submits the current valid stage once, not both review and confirmation in one event. Labels persist when fields contain text. Do not add single-letter shortcuts or hover-only actions.

After route changes, focus the page heading; after review/completion transitions, focus the stage heading; after invalid submission, focus a linked error summary or first invalid field. Edit restores relevant field focus. History page changes keep focus associated with pagination and announce the loaded page, without selecting a receipt. Back/forward, navigation, reload and reauthentication can issue reads only; they must never issue/replay Transfer POSTs. Draft passwords are cleared on leaving authentication routes. Browser Back does not restore a submitted transfer as a ready-to-submit action.

Expired authentication, protected 401 or Sign out removes saved credentials and all private view data, prevents late responses from restoring it, and makes protected history navigation require sign-in. Same-tab reload restores an existing unexpired JWT without replacing it or extending expiry. No refresh token, server logout, revocation, or cookie-auth capability is assumed. Token persistence/exposure, cross-tab behavior, browser restart and browser support baseline must be resolved by architecture before implementation; a memory-only token cannot meet reload persistence.

## Accessibility Floor

Implementation targets: keyboard operation throughout; semantic navigation/main/headings/forms/lists/description lists; associated labels and error/help descriptions; readable errors and text alternatives to color. Use visible `{colors.focus}` outlines and at least `{spacing.target-min}` control targets, with no focus obscured by sticky content. Follow the contrast targets in DESIGN.md and verify the rendered UI.

Announce page/stage, loading completion, errors and transfer outcome once at the appropriate urgency. Use `aria-busy` on updating regions and live status text; do not announce each balance digit separately or rely on a transient toast as the only receipt. Support 200% zoom and 320 CSS px wide reflow without lost actions/data. Long values wrap. Respect reduced motion; no motion is required for comprehension. These are acceptance targets, not an accessibility certification claim.

## Responsive & Platform

| Width / condition | Behavior |
|---|---|
| ≥1024 CSS px | 224px sidebar; main content max1120px with 32px gutters. Receipt/review max640px; auth max440px. |
| <1024 CSS px | Top brand/sign-out row and visible wrapping text navigation; 20px gutters. All task content stacks; no drawer. |
| Narrow history | Same semantic list and fact order; desktop columns become stacked row content with an explicit receipt link. Full money/IDs remain readable. |
| Zoom / long content | Wrap nav/actions/email/IDs; keep DOM order consistent; allow vertical growth rather than clipping or shrinking exact money. |

Show UTC instants consistently in the browser's local timezone with an unambiguous date and time (for example, “19 Oct 2026, 8:54 PM”). Detail explicitly names the local timezone/offset so a receipt's time is interpretable. Do not send altered timestamps to the backend. Locale formatting must preserve cents and cannot pass lossless money through ordinary JavaScript Number conversion.

## Key Flows

### FE-UJ-1 — Amina signs up, sees account creation confirmed with a zero USD Wallet, then signs in and opens her Dashboard.

1. Amina opens Sign up and enters name, email and password using associated labels.
2. She submits once; pending feedback prevents another concurrent registration.
3. A 201 confirms her account and zero USD Wallet; she follows Sign in. Signup does not log her in.
4. She enters credentials; a successful sign-in establishes the issued JWT/expiry and opens Dashboard.
5. **Climax:** Amina sees her name/email and the backend-confirmed `0.00 USD`, understanding the account is ready without mistaking an empty/loading value for money.

Failure: fieldErrors remain associated with inputs; duplicate email is explicit; invalid credentials remain generic. Network failure offers deliberate recovery. Passwords are cleared after request completion/navigation and never logged; read failures on Dashboard display unavailable data with retry rather than a fake zero.

### FE-UJ-2 — After Amina's demonstration Wallet is funded manually, she enters Rahim's email and a USD amount, reviews the intended Transfer, submits once, and sees the confirmed receipt and updated balance.

1. Amina refreshes Dashboard after manual demonstration funding; the UI offers no funding control.
2. She opens Send money, enters Rahim's email and an exact positive USD amount, and chooses Review transfer.
3. She checks the intended recipient email and exact amount in the inline review; Edit returns to entry.
4. She chooses Confirm transfer once; inputs and confirmation are locked during the POST.
5. **Climax:** A 201 with COMPLETED shows the confirmed receipt and returned balanceAfter. Amina can open that receipt; affected reads refresh for current data.

Failure: known validation/missing-recipient/insufficient-funds/self-transfer/conflict rejection explains correction without fabricating a failed Transaction. A lost response follows FE-UJ-4. Failed refresh after confirmed completion says the transfer completed but current data could not refresh; it does not change success to failure.

### FE-UJ-3 — Rahim signs in, opens Transaction history, and reads the same receipt as RECEIVED. Amina sees it as SENT.

1. Rahim signs in and chooses Transactions.
2. Page 0 loads with size 20 and server newest-first order; he reads direction, counterparty, exact USD amount, COMPLETED and date/time.
3. He uses bounded Previous/Next if needed and opens the selected receipt's exact identifier.
4. Detail shows sender/recipient, exact ID/amount, USD, COMPLETED and localized date/time with timezone; Back returns to history.
5. **Climax:** Rahim reads the receipt as RECEIVED; Amina, viewing her own account, reads the same exact Transaction ID as SENT.

Failure: empty history is a successful empty state; failed reads are explicitly retriable. Invalid IDs receive a safe error; unknown/inaccessible IDs share the same not-found outcome. Sign-out/identity switch clears private rows and blocks late prior-user responses.

### FE-UJ-4 — Amina reloads her signed-in browser tab and remains signed in while her JWT is valid. If it expires, she signs in again. If a Transfer response was lost, the interface explains that its outcome is unknown and guides her to review Wallet/history without replaying the request.

1. Amina reloads a protected route while her existing JWT remains valid; the same expiry applies and protected data reloads.
2. After JWT expiry or a protected 401, the UI clears saved credentials/private content and asks her to sign in. Sign out has the same local clearing effect.
3. In a later session, she confirms a transfer but its response is lost. The screen explains that completion is unknown and offers Dashboard/Transactions review.
4. If she reloads or authenticates again, no POST is resent. The applicable minimal recovery warning survives same-tab reload without restoring sensitive inputs or a replayable command.
5. **Climax:** Amina can inspect backend-confirmed Wallet/history data while the UI honestly preserves uncertainty about that particular lost request; she deliberately decides whether to start a new transfer.

Failure: failed recovery reads keep retry available without asserting the transfer failed; similar receipts cannot conclusively identify the lost request. No automatic retry/queue, silent new token, extended expiry or PENDING/FAILED Transaction record is introduced.

## Handoff and verification boundaries

Architecture must resolve lossless numeric response parsing/request serialization before client services: preserve numeric wire types and entered amount text; converting an already rounded Number cannot recover cents/IDs. Verify exact display, review, request and receipt across `0.01`, values above `9007199254740991`, maximum `99999999999999999.99`, positive BIGINT IDs above the safe-integer boundary and up to `9223372036854775807`. Reject zero/negative/over-maximum values and third fractional digits including `25.500`, without rounding. Backend remains authoritative for all balance changes.

Frontend acceptance includes all six screens against the real backend using manual/test-only funding, keyboard/narrow-viewport operation, field/code feedback, reload before/after expiry/sign-out/401, identity-change/late-response clearing, duplicate confirmation protection, unknown outcome across reload/navigation, confirmed-success/failed-refresh separation, exact money/IDs, bounded pagination and ownership-safe detail errors. Browser proxy/CORS, static/deep-link serving, framework versions and storage are architecture choices, not UX-added APIs or dependencies.
