# Editorial Review — Addendum Update

This document exists to help a Java/Spring Boot developer understand the rationale and non-binding technical guidance behind the PayNest PRD without expanding product scope.

**Structure model:** Explanation/Conceptual. The addendum moves cleanly from rationale to implementation responsibility, tooling, and deferred alternatives. The update preserved that abstract-to-concrete flow; only one small repetition remains between the concurrency rationale and the deferred-alternatives list.

**Review calibration:** Human readers; Microsoft Writing Style Guide. Content and decisions are treated as fixed. Exact source counts come from `word_metrics.py`: **636 words total**.

**Style, tone, and voice to preserve:** Concise, professional, technically specific, and explicitly non-binding. Defined domain terms, concrete examples, and direct scope guardrails support the PRD and should remain.

| Pass | Original Text | Revised Text | Changes |
|---|---|---|---|
| structure | §A.2 — PostgreSQL concurrency protection (71 words) and §D first bullet — `The exact PostgreSQL row-level locking mechanism and its Spring/JPA or SQL configuration.` | **MERGE** the deferred-mechanism statement into §A.2 and leave §D for alternatives that represent genuinely different product or data models | The mechanism is already explicitly deferred in §A.2, so the §D bullet repeats the same decision rather than introducing an alternative. Estimated impact: **−11 words**. |
| structure | §A rationale (277 words), §B responsibility guidance (121 words), §C tooling (84 words), and §D alternatives (81 words) | **PRESERVE** the rationale → responsibility → tooling → alternatives sequence and all four sections | The short document follows the Explanation/Conceptual model well. The changed concurrency, funding, and Transfer-responsibility material appears in the correct sections and does not create a second architecture document. Estimated impact: **0 words**. |
| prose | §A.2 — `Architecture may select @Lock, PESSIMISTIC_WRITE, explicit SQL, or another appropriate PostgreSQL row-level mechanism and must document its retry/error behavior.` | `Architecture may select @Lock, PESSIMISTIC_WRITE, explicit SQL, or another appropriate PostgreSQL row-level mechanism and must document its failure and retry behavior.` | Removes the slash construction and names the two behaviors clearly without selecting a mechanism. |
| prose | §A.3 — `The Transaction row is created in the same atomic unit as debit and credit.`<br>`Transaction history remains a ledger of completed transfers.` | `The Transaction row is created in the same database transaction as the debit and credit.`<br>`Transaction history remains a record of completed Transfers.` | Replaces the vague term `atomic unit` with the established database term and avoids using `ledger` for a product that explicitly has no Ledger entity. Preserves the capitalized domain term `Transfer`. |
| prose | §A.4 — `through SQL seed/fixture data, test fixtures, test-only helpers, or another explicitly non-production mechanism` | `through SQL seed data, SQL fixtures, test fixtures, test-only helpers, or another explicitly non-production mechanism` | Removes the slash construction and makes the permitted development mechanisms parallel. |
| prose | §A.5 — `distinct sender/receiver` | `distinct Sender and Recipient Wallets` | Replaces the slash construction and states the database invariant precisely using defined domain terms. |
| prose | §B — `Controllers translate HTTP input/output and contain no business logic.` | `Controllers translate HTTP requests and responses and contain no business logic.` | Uses established HTTP terminology and removes an ambiguous slash construction. |
| prose | §B — `A focused application/domain service coordinates the complete Transfer use case` | `A focused application or domain service coordinates the complete Transfer use case` | Clarifies that the architecture may select either service style; it does not prescribe a hybrid type or exact class. |
| prose | §B — `Wallet code owns ...`<br>`Transaction code owns ...` | `The Wallet module owns ...`<br>`The Transaction module owns ...` | Replaces vague `code` with the module terminology used consistently in the PRD. |
| prose | §C — `Dockerfile and Docker Compose for the application plus PostgreSQL.` | `A Dockerfile and Docker Compose for the application and PostgreSQL.` | Supplies the missing article and replaces the informal `plus` construction. |
| prose | §D — `A separate immutable attempt/audit entity`<br>`Refresh tokens, logout/revocation, account lockout, and rate limiting.` | `A separate immutable attempt or audit entity`<br>`Refresh tokens, logout and token revocation, account lockout, and rate limiting.` | Removes two slash constructions and clarifies that revocation applies to tokens. |

## Summary

- **Structure findings:** 1 small merge recommendation and 1 explicit preserve recommendation.
- **Prose findings:** 9 deduplicated recommendations focused on the updated concurrency, funding, responsibility, and deferred-scope wording.
- **Estimated structural reduction:** **11 words**, from 636 to approximately **625 words** (**1.7%**).
- **Overall assessment:** The addendum remains concise, well ordered, and appropriately subordinate to the PRD. The update did not add excessive architecture detail; most remaining work is minor terminology cleanup.
- **Comprehension trade-offs:** None material. If the repeated locking-mechanism bullet is retained in §D for scanability, it functions as intentional reinforcement rather than harmful duplication.
