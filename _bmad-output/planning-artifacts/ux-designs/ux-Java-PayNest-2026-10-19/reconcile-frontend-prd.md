# Frontend source reconciliation — draft

Sources: [frontend PRD](../../prds/prd-Java-PayNest-2026-10-19/prd.md), [handoff addendum](../../prds/prd-Java-PayNest-2026-10-19/addendum.md), and user direction recorded in [.memlog.md](.memlog.md). Both spines remain `draft`; no implementation or final approval is implied.

| Source input | Preserved in UX | Treatment |
|---|---|---|
| PRD §1–4: learning-first browser phase, vocabulary, FE-UJ-1..4, six screens/seven APIs | EXPERIENCE Foundation, IA and named Key Flows | All four journey names mirror source text. Local sign-out and unknown outcome retain source meanings. Backend authority stays upstream by reference. |
| FE-FR-1–3: registration, signin, reload lifecycle | Component Patterns, per-screen State Patterns, Interaction Primitives, FE-UJ-1/4 | Signup does not authenticate; zero Wallet confirmation; Unicode/backend-compatible validation; generic signin failure; no signup password restrictions on signin; no retained passwords. JWT/storage mechanism deferred. |
| FE-FR-4–5: dashboard and transfer | DESIGN layout/components; EXPERIENCE states, transfer recovery, FE-UJ-2/4 | Genuine zero separated from loading/failure; exact input and review; single confirmation; backend rejection vs ambiguous outcome; no optimistic money, replay or false failure after successful POST. |
| FE-FR-6–8: receipts, pagination, feedback/navigation | IA, Component Patterns, State Patterns, FE-UJ-3 | All receipt facts retained; exact ID; viewer-relative direction; COMPLETED only; 20-row bounded server pagination; safe ApiError and ownership-safe errors. |
| FE-NFR-1–5 and explicit non-goals | Both spines; EXPERIENCE accessibility and handoff | Phone/desktop and keyboard targets; full-range numeric fidelity; private-state handling; local delivery and no Docker. No added product API/feature. Source PRD remains authority for exclusions. |
| Addendum: Angular/Tailwind and minimal learning-first organization | Both Foundations and component vocabulary | No added UI/state framework. Folder/framework versions and service details remain architecture. |
| Addendum: JSON numeric loss and wire contract | EXPERIENCE Handoff | Explicit full-range money/BIGINT boundary checks; numeric wire fields remain numeric. Architecture must solve lossless transport first. |
| Addendum: authentication persistence/security tradeoffs | EXPERIENCE Interaction Primitives | Reload-before-expiry behavior fixed; architecture selects/explains mechanism and cross-tab/restart behavior. No memory-only proposal, web-storage endorsement, refresh/logout/revocation API or silent cookie/BFF. |
| Addendum: default-deny, CORS/proxy/static/deep-link serving | EXPERIENCE IA/Handoff | Architecture integration item only; no new product operation or infrastructure. |
| Addendum: offset ordering, UTC, errors, DTO differences | EXPERIENCE states, responsive, components | Fixed backend order; page0 refresh; local-time presentation chosen provisionally; no Wallet ID; history counterparty vs detail participants respected. |
| User: six Angular+Tailwind screens, “Choose for me” | Draft assumption bundles in both spines | Calm light teal/system-sans direction and compact responsive layout proposed for review. No external reference products supplied; no fabricated inspiration claims. |

No source feature or qualitative learning-first intent was intentionally dropped. Full upstream requirements are linked rather than duplicated. Remaining assumptions are visual/interaction choices; remaining integration decisions belong to architecture. Key-screen mocks and user review are pending; no opt-in Reviewer Gate was run.
