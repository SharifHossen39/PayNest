# PayNest Refined Architecture Reality Check — Final Re-review

**Review date:** 2026-10-06  
**Artifacts reviewed:** `ARCHITECTURE-SPINE.md`, `solution-design.md`, and the PayNest source PRD  
**Focus:** Closure of the RegistrationService package-cycle finding

## Verdict

**PASS — no remaining must-fixes.**

The previous User↔Wallet package cycle is closed. `RegistrationService` now lives in `auth.application`, while the direct repository coordination and single service-layer transaction remain intact.

## Dependency verification

The updated implementation graph is acyclic:

```text
Auth/Registration ---> Wallet ---> User
        |                          ^
        `--------------------------|

Transfer ----------> Transaction ---> Wallet ---> User
   |-------------------------------> Wallet
   `--------------------------------> User
```

The architecture artifacts now consistently show:

- `auth.application.RegistrationService` injecting `UserRepository` and `WalletRepository`;
- `auth` depending on `user` and `wallet`;
- `wallet.domain.Wallet` depending on `user.domain.User` for the owning unidirectional `@OneToOne`;
- no dependency from `user` back to `wallet`;
- `transfer` remaining a leaf application coordinator over User, Wallet, and Transaction persistence; and
- `transaction` depending on Wallet, with no Wallet-to-Transaction code dependency.

No Spring bean cycle is introduced. Repository interfaces remain owned and declared once by their entity features, while the higher-level Auth registration coordinator uses them under the one outer `@Transactional` boundary.

## Framework feasibility

Spring Data JPA supports a service/facade transaction spanning multiple repositories. Calls to User and Wallet repositories participate in the surrounding `RegistrationService.register` transaction, so User insertion and Wallet creation commit together or roll back together. Moving the coordinator from `user.application` to `auth.application` does not alter transaction propagation, persistence-context behavior, exception rollback rules, or the public API.

Direct repository injection remains appropriate here because registration is a small cross-feature use case. No wrapper interface or mandatory Wallet-provisioning service is required for technical correctness.

## Consistency checks

| Check | Result |
|---|---|
| Package tree places RegistrationService under Auth | Pass |
| Dependency diagram shows Auth → User/Wallet and Wallet → User | Pass |
| Narrative explicitly states User never depends on Wallet | Pass |
| Atomic-registration section names `auth.application.RegistrationService` | Pass |
| Architecture spine and solution design agree | Pass |
| Direct repository injection remains supported by one outer transaction | Pass |
| Progressive transfer plan still ends with ordered `PESSIMISTIC_WRITE` before final MVP acceptance | Pass |
| Testcontainers, lock-timeout, rollback, OpenAPI, Flyway, and Docker claims remain implementable | Pass |
| Source PRD's clear modular-monolith boundary goal is preserved | Pass |

## Final gate

**PASS. The package cycle is resolved, and there are no remaining architecture must-fixes from this reality-check sequence.**

## Primary sources

- [Spring Data JPA transactionality and multi-repository service boundaries](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
- [Spring Framework declarative transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- [Spring Data JPA repository locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [Spring Boot Testcontainers service connections](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [PostgreSQL 18 explicit locking](https://www.postgresql.org/docs/18/explicit-locking.html)
