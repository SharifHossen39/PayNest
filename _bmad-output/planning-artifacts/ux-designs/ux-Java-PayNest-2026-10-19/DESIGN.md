---
name: PayNest
description: 'Calm, legible USD wallet interface for six Angular and Tailwind screens.'
title: 'PayNest Frontend Visual Design'
status: final
created: 2026-10-19
updated: 2026-10-19
sources:
  - ../../prds/prd-Java-PayNest-2026-10-19/prd.md
  - ../../prds/prd-Java-PayNest-2026-10-19/addendum.md
colors:
  canvas: '#F7F8F5'
  surface: '#FFFFFF'
  ink: '#172B2A'
  muted: '#526361'
  primary: '#0F766E'
  primary-hover: '#115E59'
  on-primary: '#FFFFFF'
  primary-tint: '#E8F3EF'
  error: '#B42318'
  error-tint: '#FEF3F2'
  warning: '#92400E'
  warning-tint: '#FFFBEB'
  focus: '#0F766E'
  control-border: '#7C8C87'
  divider: '#DCE3DF'
typography:
  title:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 28px
    fontWeight: '650'
    lineHeight: '1.25'
    letterSpacing: -0.02em
  heading:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.4'
  body:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  label:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.5'
  meta:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  money:
    fontFamily: 'system-ui, sans-serif'
    fontSize: 32px
    fontWeight: '650'
    lineHeight: '1.25'
rounded:
  sm: 6px
  md: 10px
  lg: 14px
  full: 9999px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '5': 20px
  '6': 24px
  '8': 32px
  '10': 40px
  gutter-mobile: 20px
  gutter-desktop: 32px
  sidebar: 224px
  content-max: 1120px
  auth-max: 440px
  task-max: 640px
  target-min: 44px
components:
  app-shell:
    background: '{colors.canvas}'
    navigation: '{colors.surface}'
    active: '{colors.primary-tint}'
    width: '{spacing.sidebar}'
  auth-form:
    background: '{colors.surface}'
    radius: '{rounded.lg}'
    width: '{spacing.auth-max}'
  action-button:
    background: '{colors.primary}'
    foreground: '{colors.on-primary}'
    radius: '{rounded.sm}'
    minHeight: '{spacing.target-min}'
  form-field:
    background: '{colors.surface}'
    border: '{colors.control-border}'
    radius: '{rounded.sm}'
    minHeight: '{spacing.target-min}'
  balance-panel:
    background: '{colors.surface}'
    radius: '{rounded.lg}'
    amountSize: '{typography.money.fontSize}'
  transfer-review:
    background: '{colors.surface}'
    radius: '{rounded.md}'
    width: '{spacing.task-max}'
  transfer-result:
    background: '{colors.surface}'
    radius: '{rounded.md}'
    width: '{spacing.task-max}'
  transaction-list:
    background: '{colors.surface}'
    divider: '{colors.divider}'
    radius: '{rounded.md}'
  receipt-detail:
    background: '{colors.surface}'
    radius: '{rounded.md}'
    width: '{spacing.task-max}'
  status-label:
    foreground: '{colors.primary}'
    background: '{colors.primary-tint}'
    radius: '{rounded.full}'
  feedback-panel:
    foreground: '{colors.error}'
    background: '{colors.error-tint}'
    radius: '{rounded.sm}'
  pagination:
    foreground: '{colors.ink}'
    gap: '{spacing.2}'
    minHeight: '{spacing.target-min}'
---

## Brand & Style

Approved direction: the user delegated the visual choice with “Choose for me” and then confirmed that the proposed direction works. The light palette, system typography, component treatments, layout dimensions, and responsive choices below form the agreed visual system. Product behavior inherits the two frontend sources; this design adds no business features.

PayNest should feel like a small, understandable wallet tool. Put identity, exact USD amounts, and clear action outcomes ahead of decoration. Angular and Tailwind CSS are the chosen stack; use Tailwind's standard utility vocabulary and spacing/breakpoints, with these named product tokens as the visual delta. No extra component library or external font is proposed. This document owns appearance; [EXPERIENCE.md](EXPERIENCE.md) owns behavior. The spines take precedence over visual references on conflict.

## Colors

| Tokens | Use |
|---|---|
| `{colors.canvas}`, `{colors.surface}` | Quiet off-white page and white content surfaces. |
| `{colors.ink}`, `{colors.muted}` | Primary content and secondary explanations; muted text is still readable content. |
| `{colors.primary}`, `{colors.primary-hover}`, `{colors.on-primary}` | Primary action, links, active navigation, hover. One primary action per task stage. |
| `{colors.primary-tint}` | Selected navigation and COMPLETED text treatment; always include a label. |
| `{colors.error}`, `{colors.error-tint}` | Invalid inputs and confirmed request/business rejection. |
| `{colors.warning}`, `{colors.warning-tint}` | Unknown transfer outcome, unavailable refresh, and offline explanation; never a Transaction status. |
| `{colors.control-border}`, `{colors.divider}`, `{colors.focus}` | Control boundaries, decorative separation, visible focus. Dividers do not identify controls alone. |

Contrast targets: at least 4.5:1 for normal text, 3:1 for large text and meaningful control/focus boundaries against adjacent surfaces. Check actual rendered pairs, including primary/white, ink/canvas, muted/surface, error/error-tint and warning/warning-tint. Targets guide validation; no certification is claimed. Disabled controls keep a readable label and native disabled semantics, with reduced emphasis rather than an unreadable opacity wash.

Calculated token contrast (WCAG relative-luminance formula; rendered-state checks still required):

| Pair | Ratio |
|---|---|
| Ink / white surface | 14.84:1 |
| Muted / white surface | 6.33:1 |
| White / primary | 5.47:1 |
| Primary / canvas; primary / primary-tint | 5.13:1; 4.82:1 |
| Control-border / white surface | 3.52:1 |
| Error / error-tint; warning / warning-tint | 6.05:1; 6.84:1 |

## Typography

Use the system sans family throughout. Page titles use `{typography.title.fontSize}`; section headings use `{typography.heading.fontSize}`; body copy uses `{typography.body.fontSize}`. Labels and metadata use `{typography.label.fontSize}` and `{typography.meta.fontSize}`. Avoid uppercase paragraphs or tiny explanatory text.

Wallet balance uses the money role with tabular lining numerals. All other money uses body/heading size with tabular numerals. Display exact USD values with two fractional digits, never a compact suffix, ellipsis, or rounded approximation. Long amounts and identifiers may wrap; font size must not shrink to fit them. Email and names wrap without widening the viewport.

## Layout & Spacing

Use the Tailwind 4px scale; the named tokens pin product geometry. At 1024px and above, the app has a `{spacing.sidebar}` sidebar, `{spacing.gutter-desktop}` content gutters, and a `{spacing.content-max}` maximum main-content width. Below 1024px, use a top brand/sign-out row, visible wrapping navigation, and `{spacing.gutter-mobile}` gutters. No hidden navigation drawer is needed.

Auth content is centered up to `{spacing.auth-max}`; Send money and Transaction detail use `{spacing.task-max}`. Full width is available below those maxima. Use `{spacing.6}` between grouped sections, `{spacing.4}` inside groups, and `{spacing.2}` between a label, input and message. Minimize nested surfaces.

| Screen | Composition |
|---|---|
| Sign up | Brand, title, short explanation, vertical name/email/password fields, submit, sign-in link; success replaces the form with account/zero-Wallet confirmation. |
| Sign in | Brand, title, email/password fields, submit and sign-up link; expiry explanation sits above the form. |
| Dashboard | Heading; prominent exact balance with USD and refresh action; name/email below. Navigation supplies Send money and Transactions. No chart or history preview. |
| Send money | One task column; entry → inline recipient/amount review → result with receipt facts. Review is on the same route, never a dialog. |
| Transaction history | Heading and refresh; one semantic list, desktop grid-aligned rows and stacked mobile rows; pagination below. |
| Transaction detail | Back-to-history link, title, direction/status, receipt facts in a description list. |

Visual references: [Dashboard](mockups/key-dashboard.html) illustrates the balance, profile and navigation; [Send money](mockups/key-send-money.html) illustrates inline review and a separately labeled unknown-outcome state. These offline references use sample data and disabled submission controls. Sign up, Sign in, Transaction history and Transaction detail use this table and the experience spine; additional previews remain optional, not an implementation prerequisite.

## Elevation & Depth

Use surface tone and 1px dividers for hierarchy. Avoid decorative shadows; focus and error boundaries carry interaction state. Inline review/result stays in document flow. Loading placeholders reserve the final content region so controls do not jump.

## Shapes

Inputs/buttons use `{rounded.sm}`; review, receipt and list containers use `{rounded.md}`; the auth and balance surfaces use `{rounded.lg}`. Only compact status labels use `{rounded.full}`. Icons, when helpful, are simple line symbols accompanied by visible text on primary navigation/actions.

## Components

Names in this table match the behavioral table in EXPERIENCE.md.

| Component | Visual specification |
|---|---|
| app-shell | White navigation against canvas, current destination indicated by tinted background plus text/state. Page title is the strongest heading; sign out is a secondary text action. |
| auth-form | White bounded surface with generous inner spacing; fields remain in a single vertical sequence. Success/error content occupies the same width. |
| action-button | Teal/white primary; hover uses primary-hover. Secondary is white with control-border and ink. Minimum 44px height; visible 2px focus outline with 2px offset. Pending keeps the action label plus “…” text; disabled remains readable. |
| form-field | Persistent label above a white 44px-minimum control; control-border outline, focus outline and error boundary. Helper/error text below; password visibility is a text button within the control group. |
| balance-panel | Balance in money typography with visible USD, refresh action, and name/email in the profile section below. Loading uses labeled neutral blocks; unavailable balance uses text, never placeholder zero. |
| transfer-review | Two clearly labeled recipient/amount facts; primary confirmation and secondary edit action. Long values wrap in place. |
| transfer-result | Prominent textual outcome, then receipt facts after confirmation. Unknown outcomes use warning colors; confirmed rejections use error colors. Never decorate unknown as COMPLETED. |
| transaction-list | One consistent row template: direction, counterparty name/email, exact USD amount, COMPLETED, date/time and receipt link. Dividers separate rows. Mobile retains every fact in a stack. |
| receipt-detail | Description list with visible term/value pairs; exact ID wraps. Direction and status remain separate facts. Desktop pairs may align in two columns; phone stacks term above value. |
| status-label | Text COMPLETED with teal tint; SENT/RECEIVED remain plain visible direction text. No additional Transaction status palette. |
| feedback-panel | Visible summary, actionable explanation and optional retry/navigation action. Error or warning text/tint selected by outcome; meaning never depends on color. Field messages remain adjacent to fields. |
| pagination | Text “Page 1” represents backend page 0; Previous/Next are secondary controls with readable disabled states. Allow wrapping without moving focus unexpectedly. |

## Do's and Don'ts

| Do | Don't |
|---|---|
| Show all money digits, cents, USD and receipt IDs | Abbreviate amounts, truncate IDs or display invented balances |
| Use plain labels and consistent teal primary actions | Add gradients, decorative charts, fake cards or celebratory motion |
| Keep a single-column task flow with an inline review | Add modal confirmation stacks or hide critical facts on phones |
| Use status text and visible focus in addition to color | Treat a warning tint as proof that a Transfer failed |
| Keep this design light-only | Add a dark-mode switch or unrequested theme/dependency work |
