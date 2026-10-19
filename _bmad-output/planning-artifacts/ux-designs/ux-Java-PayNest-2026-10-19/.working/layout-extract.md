# Layout extraction reconciliation

Checked 19 October 2026 after direction approval. This is reference-to-spine reconciliation, not an accessibility audit or optional rubric review.

## References read

- Working originals: [Dashboard](key-dashboard.html), [Send money](key-send-money.html).
- Promoted references: [Dashboard](../mockups/key-dashboard.html), [Send money](../mockups/key-send-money.html).
- Contracts: [DESIGN.md](../DESIGN.md), [EXPERIENCE.md](../EXPERIENCE.md), [.memlog.md](../.memlog.md).

## Decisions illustrated

| Reference | Components and states | Spine coverage |
|---|---|---|
| Dashboard | app-shell, balance-panel, action-button; populated sample wallet, identity, last-loaded time, explicit refresh and task navigation | DESIGN Colors, Typography, Layout & Spacing, Shapes, Components; EXPERIENCE Information Architecture, Component Patterns, Dashboard State Patterns, Responsive & Platform, FE-UJ-1/2/4 |
| Send money | app-shell, transfer-review, action-button, feedback-panel/transfer-result; valid inline review and separately labeled unknown-outcome specimen | Same visual sections; EXPERIENCE transfer-review/transfer-result/feedback-panel rules, Send money State Patterns, Transfer completion and recovery, FE-UJ-2/4 |

The spines cover the reference decisions: light teal palette, system type, white navigation/content surfaces, 224px desktop sidebar at 1024px, visible wrapping narrow navigation, 32px/20px gutters, 640px task width, 44px controls, exact USD facts, inline review and one deliberate confirmation. Recovery provides wallet/history navigation without Retry transfer or projected balances. No visual reference introduces a new business feature or API.

Browser frame, sample-data banner, specimen separator/explanation and sample labels are preview scaffolding. Disabled controls and local-only links illustrate a static reference; they do not imply disabled production navigation. Both unknown-outcome and review appear only for comparison here, not simultaneously at runtime.

## Resolved alignment findings

1. Resolved: promoted Send money now uses the 20px heading role for its review amount, matching DESIGN Typography. The 32px money role remains reserved for Wallet balance.
2. Resolved: DESIGN Components/balance-panel and the Layout & Spacing Dashboard row now explicitly place balance before profile, matching the approved reference. All required facts remain present.

No meaningful reference-to-spine mismatches remain.

No behavioral decision is missing from the spines. Suggested copy and illustrative samples differ in phrasing without changing the specified outcome. Preview-specific decoration and small ungoverned spacing details are not additional product decisions.

## Six-surface coverage

| Surface | Classification | Scope |
|---|---|---|
| Sign up | Specification-only | DESIGN composition; EXPERIENCE component/state patterns and FE-UJ-1 |
| Sign in | Specification-only | DESIGN composition; EXPERIENCE component/state patterns and FE-UJ-1/3/4 |
| Dashboard | Mocked + specified | Populated wallet/profile reference; other runtime states remain specified |
| Send money | Mocked + specified | Inline review and unknown-outcome specimen; entry, submitting, rejection and completion remain specified |
| Transaction history | Specification-only | DESIGN composition; EXPERIENCE component/state patterns and FE-UJ-3/4 |
| Transaction detail | Specification-only | DESIGN composition; EXPERIENCE component/state patterns and FE-UJ-2/3 |

Coverage is two mocked surfaces and four specification-only surfaces. Static references do not verify runtime interactions, API integration or rendered accessibility.
