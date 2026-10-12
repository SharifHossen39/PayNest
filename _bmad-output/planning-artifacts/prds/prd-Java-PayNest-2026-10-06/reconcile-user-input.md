# Reconciliation Report: PayNest User Input

## Input reviewed

- **Input name:** Full PayNest PRD request supplied in the conversation on 2026-10-06
- **Artifacts compared:** `prd.md` and `addendum.md`
- **Method:** Source extraction and reconciliation only; no new product design

## Overall assessment

The artifacts are substantially aligned with the user input. The modular-monolith constraint, narrow MVP, all requested product capabilities, atomic registration and transfer behavior, PostgreSQL/JPA model, concurrency protection, error shape, business-focused testing, future microservice boundaries, phased delivery, acceptance criteria, and learning outcomes are all present. The requested 24 content areas are covered, although the PRD inserts additional sections and therefore numbers them differently.

Four minor reconciliation issues remain. None requires expanding the MVP.

## Findings

### 1. Endpoint contracts are not fully self-contained

**Severity:** Minor omission / presentation gap

The user explicitly requested that **each endpoint** define its purpose, method, URL, authentication requirement, request body, response body, validation rules, success response, and possible errors. Section 11 supplies method, URL, authentication, and success status in a shared summary table, while the endpoint subsections supply most bodies and errors. However:

- none of the endpoint subsections explicitly states a purpose;
- method, URL, and authentication must be recovered from the separate summary table;
- `GET /api/users/me` describes its response only by reference to the sign-up response's `user` object;
- the GET endpoint subsections do not explicitly say that request-body validation is not applicable, and some applicable path/query validation is only defined elsewhere.

The information is mostly recoverable across Sections 8, 11, and 13, but the endpoint definitions do not meet the requested endpoint-by-endpoint completeness literally. Each Section 11 endpoint subsection should be made self-contained without changing the API.

### 2. The test plan does not explicitly prove that history excludes unrelated transactions

**Severity:** Minor testing omission

FR-11 correctly requires that no transaction unrelated to the authenticated user be returned. Section 18 tests that an unrelated user cannot retrieve **transaction detail**, but it does not explicitly require a negative list-query test showing that `GET /api/transactions` excludes a transaction between two other users. AC-TXN-4 likewise covers only detail lookup.

This leaves the user's minimum security expectationâ€”that a user cannot access another user's private dataâ€”only partially represented in the executable test requirements. Add one integration-test requirement and matching acceptance criterion for unrelated-history exclusion.

### 3. Deleted-user JWT behavior is internally ambiguous

**Severity:** Minor conflict / underspecified error contract

Section 11.4 specifies `404` when an authenticated subject no longer resolves to a User. Section 23 says a JWT referencing a deleted User may be rejected with **`401` or a safe `404`**. Allowing either response conflicts with the user's request for consistent REST error behavior and leaves implementers/tests to invent the rule.

Choose one deterministic mapping and use it consistently across the endpoint contract, error table, risk handling, tests, and OpenAPI documentation. This decision does not need any new account-lifecycle feature.

### 4. A few non-essential operational requirements drift beyond the requested learning MVP

**Severity:** Minor scope-discipline concern

Section 19 introduces a generated/propagated correlation ID and a local performance target based on 10,000 transactions. Neither is needed to satisfy the specified MVP journeys, and neither is carried through the feature acceptance criteria. Database migrations and safe logging are consistent with the requested basic production-oriented practices, but the correlation-ID requirement and quantitative dataset target are less clearly justified under the user's instruction to avoid unnecessary enterprise complexity and unrealistic performance targets.

Either mark these two items as optional learning exercises/non-binding guidance or remove them from normative MVP requirements. No product feature should be added in their place.

## Confirmed reconciliations

- The PRD includes all 24 requested subject areas despite extra `Document Purpose`, `Glossary`, and `Assumptions and Open Questions` sections.
- The choice to persist only `COMPLETED` transactions is not a conflict: the PRD and addendum provide a strong rollback-based reason for not manufacturing durable `FAILED` records.
- Test/local fixture funding is a documented development necessity, not a public deposit feature, so it does not violate the explicit out-of-scope list.
- Pessimistic row-level locking with deterministic ordering is a reasonable single-database concurrency strategy and does not introduce distributed-system complexity.
- No microservice infrastructure is made an MVP requirement.
