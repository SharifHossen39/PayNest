# Proactive Pass 1 coverage — draft

This is the required mechanical coverage pass during spine distillation, not the optional multi-lens Reviewer Gate. Checked against the full frontend PRD and addendum on 2026-10-19.

| Pass 1 category | Coverage / remaining miss |
|---|---|
| Flow coverage | EXPERIENCE Key Flows preserves FE-UJ-1..4 names in full; each has Amina/Rahim, numbered steps, climax and failure. FE-FR-1–8 and FE-NFR-1–5 map to screens, states/components and handoff; source reconciliation records locations. No missing source journey. |
| Token completeness | DESIGN has concrete hex color tokens and typography/rounded/spacing/components frontmatter. All brace token references in both spines resolve to these paths. Load-bearing contrast targets are in DESIGN Colors; rendered contrast validation remains future work. No dark pair required for the proposed light-only direction. |
| Component coverage | Both spec tables contain the same 12 names: app-shell, auth-form, action-button, form-field, balance-panel, transfer-review, transfer-result, transaction-list, receipt-detail, status-label, feedback-panel, pagination. Each has visual and behavioral rules. |
| State coverage | EXPERIENCE State Patterns walks all six IA surfaces, with cold-load/empty/valid/focus/pending/error/offline/access states where applicable. Transfer subsection covers rejection, ambiguous outcome, success/refresh failure and reload recovery; Interaction Primitives covers expiry/signout/identity changes and late responses. No missing required state identified. |
| Visual reference coverage | `imports/` is empty; `mockups/` and `wireframes/` do not yet exist. Dashboard and Send money draft mocks remain to render; no nonexistent links or orphan supplied visuals. User has not approved spine-only treatment for the other four screens. |

Required structure: DESIGN canonical section order preserved; EXPERIENCE has all eight required sections plus Responsive & Platform and an implementation-boundary handoff. Inspiration & Anti-patterns is omitted because no external reference or rejected reference product was supplied. Sources resolve relatively to both frontend input files. Both statuses remain draft.

Open work is deliberately visible: user review of proposed direction, two key-screen mock artifacts, mock-coverage choice for four remaining screens, and optional Reviewer Gate only if requested. Architecture must resolve storage, exact numeric transport, browser baseline and integration; those are not silently chosen here.
