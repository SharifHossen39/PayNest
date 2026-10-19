# REALITY-CHECK review

Reviewed: 2026-10-19. Target: `../ARCHITECTURE-SPINE.md`, draft frontend extension, AD-13 through AD-22. Evidence: `../research-notes.md`, the primary sources below, and current repository code. No implementation or spine edits were performed.

## Verdict

**PASS for the draft proposal. No new meaningful incorrect or unsupported technology claims found.** FE-AQ-1 remains the already-declared user decision on script-readable credential persistence; this review does not accept that risk or finalize the architecture. Feasibility evidence is not a claim that the unimplemented frontend, packaging or browser tests already pass.

## Evidence checks

| Area | Result and evidence |
| --- | --- |
| Exact stack compatibility | The spine matches the registry-verified baseline in `research-notes.md`: Angular/CLI/build/compiler-cli `22.2.3`, TypeScript `6.0.3`, RxJS `7.8.2`, tslib `2.8.1`, Tailwind/plugin `4.3.3`, PostCSS `8.5.31`, lossless-json `4.3.2`, Vitest `5.0.5`, jsdom `30.1.3`, Playwright Test `1.64.1`. Node `24.20.0` and npm `11.19.0` satisfy the inspected engines. Angular compiler/build requires TypeScript `>=6.0 <6.1`; the selected version is compatible even though npm latest TypeScript is newer. Angular build accepts Vitest 5, Tailwind 4 and PostCSS 8. |
| Starter defaults | Exact CLI `v22.2.3` schema confirms standalone/strict/zoneless enabled, SSR disabled and Vitest selected by default. Routing is an explicit selection. Tailwind is a supported style option that invokes the integrated schematic. CLI unit tests use `@angular/build:unit-test` with Vitest/jsdom. The proposed starter does not require an additional Vite package or runtime service. |
| Numeric boundary | AD-15 correctly requires HTTP text before parsing, default `LosslessNumber` parsing, validated internal strings and library serialization of numeric request fields. Tagged `4.3.2` source confirms a string constructor and exact `.toString()`; `.valueOf()` can truncate decimals and is correctly prohibited. The custom parser option is `parseNumber`, as recorded in the research notes. Current `TransferRequest`, `TransferService`, and V2/V3 migrations confirm BigDecimal/NUMERIC(19,2), maximum `99999999999999999.99`, and rejection of request scales above two. `TransactionHistoryPageResponse` confirms int64 `totalElements` versus int32 page/size/totalPages. |
| HTTP semantics | Angular documents text responses and direct string-body transport, while object bodies receive automatic JSON serialization. It explicitly states that generic response types do not validate runtime data. The codec boundary and raw-string JSON request rule are therefore supported. |
| Browser storage | AD-16 accurately describes origin/tab scope, opener copies, reload/restore persistence, storage denial and script access. It avoids promising that tab closure/restart always destroys a token. The stated XSS exposure is real; HttpOnly cookie authentication would change the existing bearer-only contract. FE-AQ-1 remains an assumption, not a silently adopted security guarantee. |
| Existing operation/security contracts | Current `SecurityConfig` permits two POST auth operations, protects five exact method/path operations, uses stateless authentication, disables form/basic/logout/session behavior and denies remaining requests. Controllers and response records match the seven-operation, transfer-201, pagination and error-shape claims. Proposed UI/static handling is correctly identified as new minimal integration work, not existing behavior. |
| Browser baseline | Angular 22's documented Baseline date is `2026-05-07`. Tailwind 4 documents Chrome 111, Safari 16.4 and Firefox 128 minimum core support, with some optional utilities requiring newer features. The spine's intersection rule and limited initial Chromium acceptance are supported; they do not claim that a single Chromium run proves all browsers. |
| Development proxy | Angular documents `/api/**` proxy matching, including nested paths while excluding `/api-docs`. Relative browser API URLs through the dev proxy support the stated development arrangement without requiring backend CORS expansion. Port 4200/8080 are selected local process defaults, not a second production topology. |
| Executable-JAR packaging | `pom.xml` confirms Spring Boot `4.1.1`, Web MVC and the Boot Maven plugin. Official Boot documentation confirms classpath `/static` resources and static `index.html` support in a JAR; `src/main/webapp` would be the wrong JAR location and is not proposed. Angular documents independent `base-href` and `deploy-url` controls, supporting root client routes with `/ui-assets/` resource URLs. The explicit generated-output copy and consistent public-asset paths remain necessary implementation work. Boot's welcome page alone does not provide SPA deep-link fallback; AD-21 correctly calls for additional constrained routing and packaged verification. |
| Developer-managed database | `application.yml` uses PostgreSQL, Flyway migrations and Hibernate validation. `src/test/resources/application-test.yml` rejects connections unless `current_database()` is exactly `paynest_test`. `TestDatabaseSupport` repeats this check before cleanup/schema reset. `LocalStartupSmokeIntegrationTests` uses the dedicated test environment variables, validates the database before setup, injects that datasource into a local-profile application and uses manual/test-only SQL funding. This supports AD-22's approach without H2 or containers. The test-only profile is not automatically included in a production JAR: a future packaged smoke must explicitly point the application at the dedicated test datasource and preserve the pre-reset identity check. The spine's test safety requirement is appropriate; it is not a claim that this future harness exists. |

## Primary sources

Previously checked on 2026-10-19 and preserved with exact metadata links in `research-notes.md`:

- <https://registry.npmjs.org/@angular/build/22.2.3>
- <https://registry.npmjs.org/@angular/compiler-cli/22.2.3>
- <https://github.com/angular/angular-cli/blob/v22.2.3/packages/schematics/angular/application/schema.json>
- <https://github.com/angular/angular-cli/blob/v22.2.3/packages/schematics/angular/utility/latest-versions/package.json>
- <https://github.com/josdejong/lossless-json/blob/v4.3.2/src/LosslessNumber.ts>
- <https://github.com/josdejong/lossless-json/blob/v4.3.2/src/stringify.ts>
- <https://raw.githubusercontent.com/josdejong/lossless-json/v4.3.2/src/types.ts>
- <https://angular.dev/guide/http/making-requests>
- <https://angular.dev/guide/testing>
- <https://angular.dev/tools/cli/serve>
- <https://html.spec.whatwg.org/multipage/webstorage.html>
- <https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage>
- <https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html>

Additional targeted checks on 2026-10-19:

- <https://angular.dev/cli/build> — `base-href`, `deploy-url`, browser output and SSR options.
- <https://docs.spring.io/spring-boot/reference/web/servlet.html> — documentation identifies Boot `4.1.1`; static content, welcome page, JAR resource location and handler-order behavior.
- <https://angular.dev/reference/versions> — Angular 22 browser Baseline date and supported toolchain ranges.
- <https://tailwindcss.com/docs/compatibility> — modern-browser requirements and CSS-preprocessor limitations.

No architecture correction is required by this lens. Keep FE-AQ-1 unresolved until the user decides; prove the explicitly planned asset/fallback/security and guarded-database behavior during implementation acceptance.
