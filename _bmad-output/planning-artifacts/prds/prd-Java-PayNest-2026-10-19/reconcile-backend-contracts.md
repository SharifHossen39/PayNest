# Backend contract reconciliation

Input: frontend `prd.md` and `addendum.md`, reconciled against the backend solution design, architecture memlog, current API DTOs/services/security, `OpenApiContractIntegrationTests`, and `docs/local-development.md`.

Verdict: PASS. No meaningful backend-contract conflicts or missing integration constraints were found. FE-OQ-1 remains an explicitly recorded user decision; this reconciliation does not resolve it or finalize the draft.

| Concern | Finding and evidence |
|---|---|
| Seven APIs and product boundary | PRD section 4 maps six screens to exactly the seven existing operations. Signup is not authentication; zero USD Wallet creation, manual SQL funding, and no extra product endpoints match `OpenApiContractIntegrationTests` and the local-development runbook. |
| Exact money and BIGINT | FE-FR-5, FE-FR-7, FE-NFR-2, and the addendum preserve numeric wire types, the full monetary maximum, exact identifiers, and rejection of extra fractional zeros. The addendum correctly requires lossless parsing before conversion and exact request serialization before HTTP implementation. This matches `TransferRequest`, `TransferResponse`, `WalletResponse`, positive BIGINT path parsing, and the OpenAPI tests. |
| Authentication and privacy | FE-FR-1 through FE-FR-4 and FE-NFR-3 preserve separate signin, generic credential failure, expiry/401 recovery, local-only signout, authenticated identity, private-state clearing, and protection against late responses from a previous identity. The addendum preserves bearer-only authentication and absence of refresh/revocation/cookie capabilities. FE-FR-7 preserves the same not-found result for unrelated and nonexistent receipts. |
| Default-deny browser integration | FE-NFR-5 and the browser-delivery addendum identify the existing deny-all fallback, unavailable static/deep-link/OPTIONS routes, and absent CORS configuration. Proxy/CORS/asset-serving choices are correctly deferred to architecture while protecting exact API authorization and operation count (`SecurityConfig`). |
| Transfer ambiguity and retries | FE-FR-5 and the recovery addendum distinguish confirmed completion from response loss, prohibit automatic retry/queue/replay, prevent concurrent submissions, and state that matching history is not conclusive request identification. They correctly retain COMPLETED-only persisted receipts and acknowledge no idempotency/status API (`TransferService`, transfer DTOs). |
| Pagination and stable errors | FE-FR-6 through FE-FR-8 and the addendum preserve zero-based defaults, size bounds, fixed createdAt/id descending order, metadata-based navigation, offset-page limitations, and safe error/code/field feedback. These match `TransactionHistoryPageResponse`, `TransactionRepository`, `TransactionController`, `ApiError`, and `GlobalExceptionHandler`. |

Architecture handoff items are already explicit: choose lossless numeric transport before API-client stories; choose narrowly scoped browser delivery configuration; resolve FE-OQ-1 before authentication architecture; define simple refresh/page behavior. These are planned design work, not reconciliation gaps. Historical memlog decisions do not override current code or the refined solution design.

No PRD, addendum, or backend source changes were made by this reconciliation.
