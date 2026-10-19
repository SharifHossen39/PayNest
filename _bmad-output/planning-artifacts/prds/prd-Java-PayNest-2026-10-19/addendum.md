# PayNest frontend: architecture and UX handoff notes

This companion preserves implementation context without turning the [frontend PRD](prd.md) into a technical design. The [backend solution design](../../architecture/architecture-Java-PayNest-2026-10-06/solution-design.md) and current code remain the backend baseline. Historical memlog entries do not reinstate superseded container or earlier-version decisions.

## Adopted direction

- User-selected Angular with Tailwind CSS; TypeScript follows the Angular toolchain.
- Six browser screens over the existing seven product APIs; keep the Spring Boot modular monolith and its single database.
- Keep the learning-first approach. Concrete feature services, routing/forms, and an HTTP boundary should justify themselves; no new state-management framework or generic abstraction is required merely for extensibility.
- A `frontend/` folder, Angular CLI, standalone components, Router, reactive forms, HttpClient, and feature-based organization were proposed as a practical starting point. Their detailed organization is architecture work, not an extra product requirement.

## Integration decisions architecture must resolve

### Browser access and delivery

Current SecurityConfig allows the exact API methods and OpenAPI/Swagger resources, then denies other requests. Root/static UI paths, SPA deep links, and cross-origin OPTIONS are not currently allowed; CORS is not configured. Development needs an Angular CLI API proxy or narrowly configured CORS. Preserve the default-deny product API policy.

Serving built assets from Spring Boot is a candidate for retaining one deployment unit; choose the minimal asset/deep-link integration during architecture. Static asset/UI routes are not additional product API operations. No gateway, BFF, container, or extra runtime service is a prerequisite.

### Exact money and identifiers

Wire money is a JSON number backed by BigDecimal/NUMERIC(19,2), up to 99999999999999999.99. IDs are positive BIGINT values. Ordinary JavaScript JSON number parsing can lose cents or ID digits before subsequent formatting/conversion. Converting an already rounded number to a decimal string or BigInt does not repair it.

Select lossless response parsing and exact request serialization before HTTP service implementation. Preserve existing numeric wire types; keep entered amount text until validation/serialization. Tests should include amounts above JavaScript's safe-integer range, the monetary maximum, IDs above 9007199254740991, and rejection of 25.500. Do not move authoritative balance arithmetic to the client.

### Authentication lifetime and safety

FE-OQ-1 is resolved: preserve sign-in across reloads of a signed-in browser tab until the existing JWT expires. Reload must neither issue a replacement token nor extend expiry. After expiry, explicit local sign-out, or a protected 401, discard saved credentials/private state and require sign-in. Include tests for reload before/after expiry and after sign-out/401. Cross-tab and browser-restart behavior must be documented during architecture; the user selected reload persistence, not an additional cross-tab/restart guarantee.

Separate this desired UX from token-storage architecture. The backend issues bearer JWTs with expiresAt; it has no refresh, logout, revocation, session store, or cookie authentication contract. Local sign-out only discards frontend credentials/private state.

The referenced OWASP guidance advises against authentication-token storage in localStorage or sessionStorage because scripts/XSS can read it. Architecture must select and explain a reload-persistent mechanism and its security tradeoffs before authentication implementation. Protected-cookie/BFF alternatives can exceed the existing bearer-only scope and must not be introduced silently. Memory-only credentials alone do not satisfy the selected reload behavior. This PRD does not approve a storage mechanism or remove the need to assess script-accessible token exposure.

Scope a JWT interceptor to the intended API; never attach credentials to unrelated origins/resources. Clear private data on identity change/401 and prevent late responses from restoring the previous User's state. A route guard guides navigation but does not authorize API data.

### Transfer recovery

The API has no idempotency key, request-status lookup, or failed/pending Transaction records. A lost response or client cancellation can conceal a committed Transfer. Prevent concurrent duplicate submissions; do not implement automatic POST retries, persisted queues, or replay after reauthentication. History/balance review helps a User investigate but cannot conclusively identify a particular lost request from a matching receipt.

### DTOs, validation, and errors

Wallet returns balance/currency/createdAt without a Wallet ID. History has counterparty name/email; detail has sender/recipient name/email. Use viewer-relative SENT/RECEIVED and COMPLETED only. Preserve backend Unicode-aware name/password rules; passwords are never trimmed. Client validation must not add signup-only restrictions to signin.

ApiError is `{timestamp,status,error,message,path,fieldErrors?}`. Use safe field/code handling, including generic signin failure, 401 authentication loss, ownership-safe 404, 409 conflict, and 422 business rejection. Unknown/inaccessible detail must look the same. Prefer the current generated OpenAPI document/tests for observable contracts rather than superseded historical notes.

### History and UI presentation

Pagination is zero-based with default page 0/size 20 and size bounds 1–100. Ordering is createdAt DESC then id DESC. No search/filter/custom-sort endpoint exists; offset pagination is not a snapshot while new Transfers arrive. Define refresh/page navigation behavior simply and do not invent cursor infrastructure.

Timestamps are UTC instants. UX should choose consistent display timezone/format and accessible responsive layouts. The technical decisions above do not prescribe visual theme, components, or a dashboard aesthetic.

## Official references checked 2026-10-19

- [Angular Tailwind integration](https://angular.dev/guide/tailwind) and [Tailwind Angular setup](https://tailwindcss.com/docs/installation/framework-guides/angular): supported setup paths; exact compatible versions are selected during architecture.
- [Angular compatibility matrix](https://angular.dev/reference/versions) and [Tailwind compatibility](https://tailwindcss.com/docs/compatibility): select Angular/CLI, Node, TypeScript, RxJS, Tailwind, and browser baseline together.
- [Angular HTTP interceptors](https://angular.dev/guide/http/interceptors) and [route guards](https://angular.dev/guide/routing/route-guards): framework integration guidance; guards do not replace server authorization.
- [OWASP session management: browser storage](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#html5-web-storage-api): token-storage exposure and architectural tradeoffs.
- [Angular build environments](https://angular.dev/tools/cli/environments): frontend configuration is bundle-visible; backend secrets stay server-side.

These references inform design; they do not expand the product scope or add dependencies by themselves.
