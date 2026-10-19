# Good-spine rubric review

Target: [ARCHITECTURE-SPINE.md](../ARCHITECTURE-SPINE.md). Lens: full good-spine checklist from `.agents/skills/bmad-architecture/references/reviewer-gate.md`. Source reconciliations and mechanical lint already passed; this review evaluates semantic divergence prevention without repeating research or proposing new scope.

Verdict: the draft fixes the principal frontend divergence points and is compatible with its parent, with one medium enforcement gap around competing reads within the same authenticated session. No critical or high findings. FE-AQ-1 remains a separate, intentional user-review blocker to finalization.

## New finding

### R1 — Medium: read freshness needs a request key as well as a session generation

**Location:** AD-17 and AD-19; corresponding verification in AD-22.

AD-17 captures session generation and credential to reject responses from an older authentication context. AD-19 immediately clears the old receipt on a detail-ID change and commits history pages only after successful responses. Neither explicitly requires that the completing read still matches the current route/request selection within the same session.

For example, detail for ID A can start, navigation to ID B can clear A and start B, and A can then complete under the same valid session generation. An implementation that merely follows session-generation checks can display A under B's route. Competing history refresh/page requests can likewise commit out of order. Clearing data at navigation time does not prevent the later write. EXPERIENCE explicitly requires that a late response from a previous request cannot repopulate the cleared surface.

**Minimal concrete fix — autofix:** add one rule to AD-19: each replaceable read captures a route/query/request key or monotonically increasing read generation, and may update data/loading/error state only while it remains the current read for that surface and the current session generation. Superseded reads are cancelled or ignored. A `switchMap`-style lifecycle is one implementation choice, not a mandatory new service or abstraction. Add one AD-22 case proving an older detail response cannot replace the current ID's receipt and an older history read cannot replace the latest requested view.

This is a local enforcement clarification, not a need for a new architecture component, infrastructure, product behavior or user decision.

## Known finalization blocker — not a new finding

**FE-AQ-1 / AD-16:** user review of sessionStorage's script-readable JWT exposure remains pending by design. The draft labels the assumption and risk, documents tab/restart/storage-failure semantics, and excludes silent memory-only or cookie/BFF substitution. Keep status `draft` until the user accepts or revises that proposal. This review does not accept it on the user's behalf.

## Rubric assessment

| Dimension | Assessment |
|---|---|
| Actual divergence prevention | Strong: fixes one HTTP/codec boundary, root service ownership, wire/internal numeric types, credential scope, transfer dispatch/recovery, route set, read states and delivery integration. R1 is the remaining local freshness ambiguity. |
| Enforceable AD rules | AD-13..22 generally specify observable invariants and tests. Exact numeric tokens, marker-before-dispatch, generation-safe 401 behavior and reserved-path fallback exclusion are testable. R1 tightens the same-session read case. |
| Scope and altitude | Appropriate for six screens in a single-developer learning project. No mandatory global store, generic hierarchy, BFF, container or extra persisted domain. Exact micro-class/CSS choices are correctly left to implementation. |
| Operational/environmental envelope | Decided proportionately: two local development processes, API proxy, same-origin combined JAR, opt-in browser packaging, no Node prerequisite for ordinary backend work, guarded test database and explicit public-hosting/TLS deferral. No missing production-provider project is warranted by this scope. |
| Parent compatibility and brownfield ratification | Preserves backend AD-1..12, numeric wire types, seven product APIs, default-deny method/path policy, stateless bearer auth, exact schema, atomic transfer semantics and direct-local PostgreSQL delivery. UI resources are separated from product operations. |
| Capability coverage | FE-FR-1..8, FE-NFR-1..5 and FE-UJ-1..4 all map to owning decisions and proportionate verification. Approved UX appearance/interaction rules are incorporated by reference without creating an additional feature backlog. |
| Deferred boundaries | FE-AQ-1 is explicitly owned; public rollout and broader browser testing are bounded rather than silently promised. Deferred names/presentation extraction cannot change contract or ownership choices. |
| Technology evidence | Exact compatible pins and primary-source verification are supplied in `research-notes.md`; the spine distinguishes direct pins from implementation lockfile transitive resolution. No new research was required for this gate. |

No spine, source planning document or implementation file was edited during this review.

## Targeted closure recheck

R1 is closed. AD-19 now requires a per-surface request generation and route/page key independent of the login generation; only the newest matching request may commit data, errors or loading completion, and superseded reads are cancelled or ignored. AD-22 explicitly requires out-of-order same-session detail/history/refresh coverage. These changes address both stale-data replacement and stale loading/error completion without introducing another component or service.

Targeted verdict: PASS, no remaining gap for R1. FE-AQ-1 remains pending user review as planned. This recheck inspected only the accepted finding and its verification rule; it did not restart the full review or approve unrelated changes.
