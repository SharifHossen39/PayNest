---
title: 'Story 4.2: Publish and Verify the OpenAPI Contract'
type: 'feature'
created: '2026-10-19'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '1a16b8d290cb4e38ffa8a38bf670909fb3f43069'
context:
  - _bmad-output/implementation-artifacts/epic-4-context.md
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PayNest serves generated OpenAPI documents, but the seven product operations are not intentionally described or verified, so authentication, validation, success, error, and DTO contracts can drift without detection.

**Approach:** Add concise springdoc configuration and API-layer metadata, then verify the generated `/v3/api-docs` document with a focused integration suite. Keep runtime behavior unchanged and make the generated contract the tested artifact rather than maintaining a separate handwritten specification.

## Boundaries & Constraints

**Always:** Publish exactly the seven existing product operations under the Authentication, User, Wallet, and Transactions tags; define `bearerAuth` as HTTP bearer JWT; leave signup/signin public and mark the other five operations protected; document exact success statuses, request/parameter constraints, pagination defaults and bounds, positive BIGINT transaction IDs, decimal money semantics, representative shared `ApiError` responses, and safe invented examples; expose DTO schemas only; keep OpenAPI JSON/YAML and Swagger UI publicly accessible.

**Never:** Add or change endpoints, entities, migrations, dependencies, runtime validation, business rules, response payloads, HTTP statuses, authorization behavior, transaction behavior, or persistence mappings. Do not publish persistence entities, internal relationships, authenticated-principal parameters, real identities, credentials, JWTs, hashes, secrets, or Story 4.3 runbook work; do not add code generation, grouped APIs, Docker, or microservice infrastructure.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Public documentation | Anonymous request, with or without a stray bearer value, to OpenAPI or Swagger UI assets | Documentation remains reachable without weakening product routes | Existing safe security behavior remains unchanged |
| Operation inventory | Generate `/v3/api-docs` | Exactly the seven approved method/path pairs appear; no documentation asset or extra product operation appears | Contract test fails on omission or expansion |
| Authentication contract | Inspect signup/signin and five protected operations | Auth operations have no effective security requirement; protected operations require `bearerAuth` | Representative 401 responses reference `ApiError` |
| Input constraints | Inspect signup, signin, transfer, history, and detail inputs | Required fields, lengths/formats, money limits, page defaults/bounds, and positive `int64` ID are explicit | Documented 400/409/422 responses reference `ApiError` as applicable |
| Response contract | Inspect every success and representative failure response | Signup/transfer use 201; the other five use 200; responses resolve to API DTOs and shared errors | No entity or internal-principal schema is exposed |
| Examples and money | Inspect examples and monetary properties | Identities are invented, tokens/secrets are placeholders, and money is decimal with two-fraction-digit semantics | No sensitive or floating-point-style contract is published |

</frozen-after-approval>

## Code Map

- `pom.xml` — already supplies springdoc 3.1.1; no dependency change is expected.
- `src/main/java/com/paynest/common/config/OpenApiConfig.java` — define API information and the reusable `bearerAuth` security scheme without a global security requirement.
- `src/main/java/com/paynest/auth/api/AuthController.java`, `user/api/UserController.java`, `wallet/api/WalletController.java`, `transfer/api/TransferController.java`, and `transaction/api/TransactionController.java` — describe tags, operations, exact responses, per-operation security, parameters, and hidden authenticated principals.
- API request/response records under `auth/api`, `user/api`, `wallet/api`, `transfer/api`, and `transaction/api`, plus `common/error/ApiError.java` — describe only externally observable schemas, constraints, examples, and decimal/date-time semantics.
- `src/main/java/com/paynest/auth/security/SecurityConfig.java` — existing public docs rules and protected product rules are invariants; production changes are not expected.
- `src/test/java/com/paynest/common/config/OpenApiContractIntegrationTests.java` — parse the generated JSON and freeze inventory, security, validation, response, error, and non-exposure contracts.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/com/paynest/common/config/OpenApiConfig.java` — add API metadata and the HTTP bearer/JWT scheme using the existing springdoc dependency.
- [x] Five API controllers — annotate the seven operations with stable tags, summaries, request/parameter details, exact success/error responses, protected-operation security, and hidden principals.
- [x] API DTO records and `ApiError.java` — add schema metadata for required fields, validation bounds, formats, safe examples, DTO shapes, decimal money, and optional `fieldErrors` without changing serialization or runtime validation.
- [x] `src/test/java/com/paynest/common/config/OpenApiContractIntegrationTests.java` — verify the generated document, including exactly seven operations, public/protected separation, all endpoint contracts, shared errors, and absence of persistence/internal schemas.
- [x] Existing HTTP and inventory suites — retain public-doc access, default-deny security, seven-endpoint, three-entity, and prior behavior coverage.

**Acceptance Criteria:**
- Given the running application, when `/v3/api-docs`, `/v3/api-docs.yaml`, or Swagger UI is requested anonymously, then documentation is available while all five protected product operations still require a valid JWT.
- Given the generated OpenAPI document, when the contract suite inspects it, then it contains exactly the approved seven operations with correct tags, security, success statuses, inputs, DTO responses, and representative `ApiError` responses.
- Given schema components and examples, when inspected, then they expose no persistence entity, relationship, authenticated principal, or sensitive value and accurately describe decimal money and validation rules.
- Given the completed documentation work, when the full PostgreSQL-backed suite runs, then all existing product, security, transaction, endpoint-inventory, and entity-inventory behavior remains unchanged.

## Implementation Notes

- Added one OpenAPI configuration bean, controller documentation for seven operations, API DTO schema annotations, and six generated-contract integration tests. Runtime service/security/persistence code and dependencies are unchanged.
- Swagger's OpenAPI 3.1 generation requires `types = "number"` alongside decimal metadata for BigDecimal, and explicit JSON response media types. The test reader uses BigDecimal parsing to verify the exact monetary ceiling without double rounding.
- Three independent review layers completed; accepted findings were patched and verified. No findings were deferred. Review corrections cover credential metadata, normalization/scale descriptions, safe error examples, exact parameter/component inventories, and nested response requirements/enums/formats.
- Final verification on Java 25 and developer-managed PostgreSQL 18.6: `mvn.cmd test` passed **188 tests, zero failures/errors/skips** on 2026-10-19. The focused OpenAPI/profile/wallet run also passed 19 tests. `git diff --check` passed.
- Matrix audit: public docs are covered by `anonymousDocumentationAccessDoesNotRelaxProtectedProductRoutes` and the existing public-route security test; operation inventory by `documentContainsExactlyTheSevenApprovedTaggedOperations`; authentication by `bearerAuthenticationIsDeclaredOnlyForTheFiveProtectedOperations` and existing transfer access tests; inputs by `inputConstraintsPaginationAndPositiveBigintIdAreExplicit`; responses by `successAndRepresentativeFailureResponsesReferenceOnlyApiDtos`; examples/money/DTO exposure by `examplesAreInventedAndEveryMoneyPropertyUsesDecimalTwoFractionSemantics`. All covering tests ran and passed in the final full suite.

## Spec Change Log

## Review Triage Log

| Finding | Verdict / route | Evidence |
|---|---|---|
| Blind: transfer scale is stricter than numeric multipleOf | medium / patch | `normalizeAmount` rejects scale above two, including trailing zeros; a short schema description must explain this lexical restriction. |
| Blind: signin/recipient email normalization is undocumented | low / patch | Both services use the existing normalizer; documenting its whitespace/lowercase behavior is a direct metadata correction. |
| Blind: password fields lack credential metadata | low / patch | Request password properties currently appear as ordinary strings; password format and write-only schema metadata improve documentation without altering serialization. |
| Blind: generic validation example is reused on other error statuses | medium / patch | ApiError examples default to status 400 and signup path even for documented 401/409/422 responses; representative response-specific safe examples prevent misleading output. |
| Blind: anonymous transfer access is not tested | false / reject | Existing `TransferHttpIntegrationTests` explicitly asserts anonymous POST rejection and ran successfully in the full suite; coverage need not be duplicated in this class. |
| Blind: schema denylist does not freeze DTO-only output | medium / patch | The new contract suite would accept an extra renamed internal schema; asserting the approved schema set directly protects the published DTO boundary. |
| Blind: unexpected principal parameters are not rejected by tests | medium / patch | Current tests locate expected parameters but allow extras; exact parameter names/locations prevent a scalar principal leak from passing. |
| Blind: nested response fields and enums lack assertions | medium / patch | Top-level references and money checks do not detect incorrect nested refs, required fields, currency/status/direction values, or date-time formats. |
| Blind: YAML/UI access coverage is insufficient | false / reject | Existing `CurrentUserProfileHttpIntegrationTests.publicRoutesIgnoreStrayBearerWhileUnlistedProductRoutesRemainDenied` covers YAML, swagger-config, and UI with stray bearer values and ran successfully; a second full-format equivalence/UI asset suite is not needed for the stated access guarantee. |
| Verification: required response fields and constrained values can regress | medium / patch | The filed mutation example changes WalletResponse currency/required metadata without affecting current assertions; add generated-schema assertions alongside the nested-response coverage. |

## Design Notes

Define the bearer scheme globally but attach its requirement only to protected operations. This keeps public authentication operations unambiguous and avoids overrides required by a global security declaration. Document service-enforced constraints with OpenAPI metadata only; adding Bean Validation would alter runtime behavior and is outside this story.

## Verification

**Commands:**
- `mvn.cmd '-Dtest=OpenApiContractIntegrationTests,CurrentUserProfileHttpIntegrationTests,CurrentWalletHttpIntegrationTests' test` with the required `PAYNEST_TEST_DB_*` variables set — generated-contract, public-doc security, and endpoint/entity inventory checks pass.
- `mvn.cmd test` with the required `PAYNEST_TEST_DB_*` variables set — the full PostgreSQL-backed regression suite passes.
- `git diff --check` — no whitespace errors.
