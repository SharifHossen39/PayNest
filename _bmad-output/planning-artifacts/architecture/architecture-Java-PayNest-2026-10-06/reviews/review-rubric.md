# Architecture Rubric Gate - PayNest MVP

## Gate verdict - PASS

The refined architecture remains safe for downstream epic, story, and implementation work. Amended AD-3 permits direct repository use without dissolving feature ownership, the MUST/LATER/GUIDANCE vocabulary preserves every final PRD acceptance requirement, and the design still contains exactly three persisted entities and seven MVP product operations.

There are no critical or high findings and no must-fix findings. Two low-severity documentation notes remain: promote the spine from `draft` to `final` when the parent finalization workflow closes, and align one capability-map location with the direct-repository design.

## Review basis

Reviewed:

- `ARCHITECTURE-SPINE.md`
- `solution-design.md`
- Final PayNest `prd.md` and its acceptance criteria
- The BMad architecture Good-spine checklist in `references/reviewer-gate.md`
- The architecture skill's spine contract and headless review guidance

The deterministic spine linter was run directly with Python because `uv` could not create its cache outside the writable workspace. It returned `ok: true` with zero findings.

## Focused gate results

### 1. Amended AD-3: direct repository use without incompatible builds - PASS

AD-3 still fixes the decisions on which independent implementation units could otherwise diverge:

- Controllers are limited to HTTP validation and mapping; they do not own business decisions or repository orchestration.
- Each repository is declared exactly once by the feature that owns its entity.
- Direct repository use is limited to focused application coordinators, not made a general cross-package convenience.
- Registration has one outer use-case boundary and may use User and Wallet repositories directly.
- Transfer has one required `TransferService` boundary and may use User, Wallet, and Transaction repositories directly.
- Transaction query work may use Wallet identity and Transaction persistence without creating duplicate repositories.
- `common` remains feature-neutral, and the dependency graph prevents an Auth/User cycle.
- Helper services and interfaces are demand-driven refactorings, not mandatory indirection.

This is sufficiently deterministic for compatible builds. Two builders can differ on whether they later extract `WalletTransferAccess` or `TransactionRecordingService`, but that difference is internal refactoring rather than contract divergence: repository ownership, allowed dependency direction, transaction ownership, locking, persistence behavior, API behavior, and tests stay the same.

The companion design makes the intended edges concrete:

```text
auth.application/security -> user.persistence.UserRepository
user.application.RegistrationService -> user.persistence.UserRepository
user.application.RegistrationService -> wallet.persistence.WalletRepository
transfer.application.TransferService -> user.persistence.UserRepository
transfer.application.TransferService -> wallet.persistence.WalletRepository
transfer.application.TransferService -> transaction.persistence.TransactionRepository
transaction.application.TransactionQueryService -> wallet.persistence.WalletRepository
transaction.application.TransactionQueryService -> transaction.persistence.TransactionRepository
```

The relaxation therefore removes speculative wrapper services while retaining module ownership and a reviewable seam. It does not authorize controller-to-repository access, duplicate repositories, feature-neutral domain leakage, or arbitrary cyclic dependencies.

### 2. MUST / LATER / GUIDANCE classification - PASS

The classification is explicit and does not weaken the final MVP contract:

- **MUST IMPLEMENT FOR MVP:** required product behavior or acceptance evidence.
- **IMPLEMENT LATER DURING LEARNING:** sequencing only; the item remains required before final MVP acceptance.
- **ARCHITECTURAL GUIDANCE ONLY:** optional class extraction and similar internal refactoring, not product behavior.

All PRD acceptance-bearing concerns remain mandatory by final acceptance:

| PRD requirement | Architecture classification/evidence | Verdict |
|---|---|---|
| Atomic registration and Wallet creation | Registration transaction is MUST; rollback is an essential test | Pass |
| Authentication, JWT, ownership | Security flow is MUST; final token and ownership tests are required | Pass |
| Atomic Transfer and completed Transaction | Transfer transaction is MUST; rollback and record assertions are required | Pass |
| Concurrent overspend prevention | Pessimistic locking and deterministic order are required for final Transfer correctness | Pass |
| Conflict/timeout verification | Later learning stage, explicitly required before final acceptance | Pass |
| Transaction history/detail and pagination | Required product operations with participant-constrained queries and tests | Pass |
| Bounded query/N+1 behavior | Advanced hardening test required before final hardening/acceptance | Pass |
| Exact errors and safe disclosure | Binding AD-9 contract plus integration verification | Pass |
| OpenAPI | Required, scheduled later, with exactly seven product paths asserted | Pass |
| Docker Compose delivery | Required final delivery phase, scheduled later | Pass |
| Microservices/Kafka/cache/saga/outbox | Not MVP; future learning only | Pass |

The roadmap's early unlocked Transfer is clearly described as a learning checkpoint rather than a completed MVP. Concurrency controls, lock ordering, rollback proof, exact error behavior, OpenAPI, and Docker all occur before final acceptance. No PRD acceptance criterion has been relabeled as optional guidance.

### 3. Exactly three persisted entities - PASS

The only persisted domain entities are:

1. `User`
2. `Wallet`
3. `Transaction`

AD-2 expressly prohibits additional persisted concepts. The structural seed and solution domain section contain those three entities only. `TransactionStatus` is an enum, and `Transfer` is an application use-case slice with no entity, table, or repository. The schema defines only `users`, `wallets`, and `transactions`.

This matches PRD AC-QUALITY-4 and preserves the intended JPA learning relationships:

- Wallet owns the unique one-to-one User foreign key.
- Transaction owns the sender-Wallet and receiver-Wallet many-to-one foreign keys.
- Wallet has no unbounded Transaction collections.

### 4. Exactly seven MVP product operations - PASS

The API table has exactly seven product mappings:

1. `POST /api/auth/signup`
2. `POST /api/auth/signin`
3. `GET /api/users/me`
4. `GET /api/wallet`
5. `POST /api/wallet/transfers`
6. `GET /api/transactions`
7. `GET /api/transactions/{id}`

No funding, health, admin, refresh-token, logout, Wallet-management, or other convenience operation is introduced. AD-9 correctly distinguishes OpenAPI/Swagger documentation resources from product operations, and the OpenAPI test asserts the seven PRD product paths. The local SQL funding helper is explicitly non-production and not an HTTP operation.

## Good-spine checklist

| Rubric dimension | Verdict | Evidence |
|---|---|---|
| Fixes real divergence points | **Pass** | AD-1 through AD-12 settle topology, ownership, seams, transactions, locking, schema, auth, API/errors, verification, and delivery. |
| Rules prevent stated divergence | **Pass** | Every AD has `Binds`, `Prevents`, and an actionable `Rule`; companion mechanics agree with the spine. |
| Deferred hides no required choice | **Pass** | Only post-MVP features, distributed infrastructure, speculative patterns, and unneeded production topology are deferred. |
| Named technology is fit/current | **Pass** | The pinned baseline is internally consistent; springdoc remains `3.1.1`, and the fixed PBKDF2 profile remains explicit. |
| Ratifies brownfield reality | **N/A** | This is a greenfield learning project. |
| Covers the driving PRD | **Pass** | Capability mapping covers FR-1 through FR-14; all acceptance groups remain represented. |
| Parent-spine compatibility | **N/A** | No inherited parent spine exists. |
| Initiative dimensions owned | **Pass** | Application shape, data, state mutation, concurrency, security, API, errors, testing, migration, configuration, delivery, and future boundaries are decided or deferred. |
| Downstream usability | **Pass** | Stable ADs, structural seed, dependency map, schemas, API table, test plan, and roadmap provide implementable guidance without forcing speculative classes. |

## Cross-contract verification

### Transaction and data integrity - PASS

- Registration and Transfer each have one outer application transaction.
- Transfer locks both Wallet rows in ascending Wallet-ID order at `READ COMMITTED`.
- Balance and recipient checks are repeated while locks are held.
- Debit, credit, and the single `COMPLETED` Transaction either all commit or all roll back.
- `NUMERIC(19,2)`, `BigDecimal`, precision/scale rejection, and non-negative/positive database checks remain fixed.
- Separate-transaction Testcontainers tests prove concurrency behavior against PostgreSQL.

### Security and data ownership - PASS

- Only sign-up, sign-in, and documentation assets are public.
- Protected product operations derive the User ID from a verified JWT principal.
- User and Wallet ownership IDs are not accepted from caller input.
- Transaction detail is constrained by participant Wallet ID, and unrelated/unknown IDs share `404`.
- The full 8-72 Unicode-character password is processed through the pinned PBKDF2 profile without truncation.
- Error responses and logs exclude secrets and persistence internals.

### Scope and learning fit - PASS

- One Spring Boot deployable and one PostgreSQL database remain the only runtime application/data units.
- Direct repositories make the small use cases visible without adding a repository-abstraction layer.
- Advanced correctness is sequenced after the basic learning flow but remains part of Definition of Done.
- Future microservice technologies are documented only; no preparatory broker, gateway, outbox, saga, or split database is introduced.

## Non-blocking findings

### LOW - Promote the spine status after the gate closes

`ARCHITECTURE-SPINE.md` still has `status: draft`. That is appropriate while this gate is running. After the parent finalization workflow accepts the review, promote it to `final` and record the finalization according to the architecture workflow. This does not affect technical readiness.

### LOW - Align the signup capability-map location

The spine capability map says FR-1..FR-2 live in `auth.api`, `user.application`, and `wallet.application`, while the refined implementation deliberately has `RegistrationService` use `wallet.persistence.WalletRepository` directly. The binding AD-3/AD-4 rules, structural seed, and companion dependency map are clear, so this cannot reasonably produce an incompatible implementation; however, changing that capability-map cell to `wallet.persistence` (or `wallet domain/persistence`) would make source extraction perfectly literal.

## Mechanical verification

- Spine linter: **pass**, zero findings.
- AD identifiers: contiguous AD-1 through AD-12; no duplicates.
- Every AD includes `Binds`, `Prevents`, and `Rule`.
- No unresolved placeholders or template comments.
- Stack entries are pinned.
- Solution API table contains exactly seven `/api` product rows.
- Solution domain section defines exactly User, Wallet, and Transaction.
- No fourth entity, Transfer repository/table, or extra product endpoint appears.
- Exact PRD error envelope/codes, PBKDF2 profile, springdoc `3.1.1`, and product-operation wording remain intact.

## Final conclusion

**PASS. No must-fix findings remain.** The refinement reduces unnecessary service/interface scaffolding while preserving the contracts that matter: single repository ownership, explicit use-case transaction boundaries, fixed dependency direction, exact product/API scope, data consistency, security, and final acceptance evidence.
