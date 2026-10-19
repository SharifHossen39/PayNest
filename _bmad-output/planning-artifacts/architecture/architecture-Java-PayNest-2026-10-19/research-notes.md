# Frontend architecture research evidence

Checked: 2026-10-19. Scope: primary-source evidence for the Angular browser SPA; no backend contract changes or implementation. Selected packaging candidate: Angular static build served inside the existing Spring Boot executable JAR. The proxy discussed below is development-only.

## Verified package baseline

Live npm registry maintainer metadata was read with read-only `Invoke-RestMethod` requests. Exact stable pins form a compatible baseline, rather than independently choosing every package's latest release.

| Package | Exact version | Primary metadata |
| --- | --- | --- |
| `@angular/core`, `@angular/common`, `@angular/compiler`, `@angular/forms`, `@angular/platform-browser`, `@angular/router` | `22.2.3` | <https://registry.npmjs.org/@angular/core/22.2.3> |
| `@angular/cli`, `@angular/build`, `@angular/compiler-cli` | `22.2.3` | <https://registry.npmjs.org/@angular/cli/22.2.3>, <https://registry.npmjs.org/@angular/build/22.2.3>, <https://registry.npmjs.org/@angular/compiler-cli/22.2.3> |
| `typescript` | `6.0.3` | <https://registry.npmjs.org/typescript/6.0.3> |
| `rxjs` | `7.8.2` | <https://registry.npmjs.org/rxjs/7.8.2> |
| `tslib` | `2.8.1` | <https://registry.npmjs.org/tslib/2.8.1> |
| `tailwindcss`, `@tailwindcss/postcss` | `4.3.3` | <https://registry.npmjs.org/tailwindcss/4.3.3>, <https://registry.npmjs.org/@tailwindcss/postcss/4.3.3> |
| `postcss` | `8.5.31` | <https://registry.npmjs.org/postcss/8.5.31> |
| `vitest` | `5.0.5` | <https://registry.npmjs.org/vitest/5.0.5> |
| `jsdom` | `30.1.3` | <https://registry.npmjs.org/jsdom/30.1.3> |
| `@playwright/test` | `1.64.1` | <https://registry.npmjs.org/@playwright/test/1.64.1> |
| `lossless-json` | `4.3.2` | <https://registry.npmjs.org/lossless-json/4.3.2> |

- Installed Node `24.20.0` and npm `11.19.0` satisfy the retrieved engine requirements. Angular `22.2.3` accepts Node `^22.22.3 || ^24.15.0 || >=26.0.0`; its CLI accepts npm `^6.11.0 || ^7.5.6 || >=8.0.0`.
- Angular compiler/build requires TypeScript `>=6.0 <6.1`. TypeScript's live latest was `7.0.2`, which must not replace the compatible `6.0.3` pin. Angular accepts RxJS `^6.5.3 || ^7.4.0`.
- Angular build `22.2.3` accepts Vitest `^4.0.8 || ^5.0.0`, PostCSS `^8.4.0`, and Tailwind `^2.0.0 || ^3.0.0 || ^4.0.0`. Its own schematics template selects TypeScript `~6.0.2`, Vitest `^5.0.0`, and jsdom `^30.0.0`.
- Vitest `5.0.5` accepts Node `^22.12.0 || ^24.0.0 || >=26.0.0`; jsdom `30.1.3` accepts `^22.22.2 || ^24.15.0 || >=26.0.0`; Playwright Test `1.64.1` accepts Node `>=20`.
- Optional coverage dependency, if selected: verified `@vitest/coverage-v8@5.0.5` matches Vitest `5.0.5`; it is not needed for the default unit-test baseline.

Supporting primary sources: <https://angular.dev/reference/versions>; <https://github.com/angular/angular-cli/blob/v22.2.3/packages/schematics/angular/utility/latest-versions/package.json>.

## Angular CLI and Tailwind setup evidence

The exact CLI `v22.2.3` application schema defaults to `standalone: true`, `strict: true`, `zoneless: true`, `ssr: false`, and `testRunner: vitest`. Routing remains an explicit scaffold selection. `--style=tailwind` is supported and invokes the integrated Tailwind schematic. An explicit scaffold should select standalone, strict, zoneless, routing, SSR disabled, Tailwind, Vitest, and npm; it should avoid initializing a nested Git repository.

Sources: <https://angular.dev/cli/new>; <https://github.com/angular/angular-cli/blob/v22.2.3/packages/schematics/angular/application/schema.json>; <https://github.com/angular/angular-cli/blob/v22.2.3/packages/schematics/angular/application/index.ts>.

The supported manual Tailwind setup uses `.postcssrc.json` with the `@tailwindcss/postcss` plugin and `@import "tailwindcss";` in the global CSS stylesheet. The candidate uses CSS. Unit testing uses `@angular/build:unit-test`, Vitest and jsdom; `ng test --no-watch` is a deterministic single-run invocation. A direct Vite dependency or a separate Vitest configuration is unnecessary for this baseline. Playwright Test remains the separate browser E2E runner.

Sources: <https://angular.dev/guide/tailwind>; <https://tailwindcss.com/docs/installation/framework-guides/angular>; <https://angular.dev/guide/testing>.

## Lossless JSON boundary

The npm metadata identifies `lossless-json@4.3.2`, maintained by Jos de Jong, published 2026-10-16, with ESM exports and bundled TypeScript declarations. Its browser-compatible parser defaults to preserving numeric lexemes as `LosslessNumber` instances.

The tagged implementation, rather than inconsistent README examples, establishes the API:

- `parse(text, reviver?, options?)`, with options property **`parseNumber`**, not `numberParser`; its callback receives the numeric lexeme as a string. Parsing returns `unknown` and must be followed by contract-specific validation.
- `new LosslessNumber(value: string)` validates JSON numeric syntax; `.toString()` returns the original numeric string.
- `.valueOf()` may produce a JavaScript number for decimal values even when decimal digits are truncated. It is unsuitable for money or identifier conversion.
- `stringify(value, replacer?, space?, numberStringifiers?)` serializes `LosslessNumber` directly as a JSON numeric token. It returns `string | undefined`. A custom number stringifier has `test(value)` and `stringify(value)` functions; its output must be valid JSON number text.
- Ordinary `JSON.stringify` does not provide this numeric-token behavior. `LosslessNumber` deliberately has no `toJSON()` implementation. Do not pass these wrappers to Angular's automatic object-body serialization.

Sources: <https://registry.npmjs.org/lossless-json/4.3.2>; <https://github.com/josdejong/lossless-json/blob/v4.3.2/src/parse.ts>; <https://raw.githubusercontent.com/josdejong/lossless-json/v4.3.2/src/types.ts>; <https://github.com/josdejong/lossless-json/blob/v4.3.2/src/LosslessNumber.ts>; <https://github.com/josdejong/lossless-json/blob/v4.3.2/src/stringify.ts>.

Architecture implications inferred from those APIs: request response bodies as HTTP text, parse before any native JSON conversion, validate DTO shape and numeric tokens, and immediately map amounts and BIGINT identifiers to plain strings internally. Wrap only contractually numeric request fields in `LosslessNumber` before library serialization; never convert every numeric-looking text field. Preserve passwords, emails, and other actual string fields as strings. No JavaScript `Number`, `parseFloat`, number-input conversion, or numeric formatting pipe may sit on an amount/identifier path. Precision `19,2` permits at most 17 integer digits; validation and normalization must respect the actual backend constraints. Use string manipulation for display and optional integer-minor-unit `BigInt` arithmetic where arithmetic is required. Precision verification fixtures must contain literal raw JSON text, not JavaScript numeric literals that may already have lost digits.

## HTTP and development proxy

Angular `HttpClient` supports `responseType: 'text' as const`. Its generic type parameter is an assertion, not runtime validation. A string request body is sent directly, while an object is automatically serialized as JSON. Therefore a losslessly serialized request must be supplied as a string with `Content-Type: application/json`; success/error bodies need explicit decoding, and empty or non-JSON bodies need explicit handling.

Source: <https://angular.dev/guide/http/making-requests>.

Development `ng serve` can forward `/api/**` to the existing local backend through `proxyConfig`. This pattern includes `/api` and nested API paths without including `/api-docs`; changing the proxy configuration requires restarting the development server. This is development tooling only. The selected deployment candidate serves the Angular build from the existing Spring Boot executable JAR, with appropriate same-origin API routing and SPA navigation fallback handled there.

Source: <https://angular.dev/tools/cli/serve>.

The CLI build `deploy-url` option controls resource URLs in index/component stylesheets. Spring Boot serves classpath `/static` resources and supports a static index welcome page. Packaging those resources in the existing JAR is compatible with one runtime deployment; SPA navigation forwarding and its reserved-path/security rules still require explicit integration tests.

Sources: <https://angular.dev/cli/build>; <https://docs.spring.io/spring-boot/reference/web/servlet.html>.

## Bearer persistence limits

`sessionStorage` is scoped by origin and top-level browsing context. It survives reload and browser restore. A newly opened tab with an opener can receive an initial copy of the opener's session storage; subsequent changes are separate. Closing the tab/window ends the page session in ordinary operation, but restoration behavior means the design must not promise that closing a tab always destroys the token. Storage access can fail when browser policy disallows persistence.

Sources: <https://html.spec.whatwg.org/multipage/webstorage.html>; <https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage>.

Both sessionStorage and localStorage are accessible to injected same-origin JavaScript; shorter persistence does not remove XSS exposure. Memory reduces persistence and loses the login on reload, but active injected code can still act as the user. HttpOnly cookies reduce direct JavaScript token access but require backend authentication/session and CSRF decisions. They cannot silently replace the existing bearer-only contract.

The sessionStorage candidate is a learning-project convenience requiring explicit visibility in the user's architecture review, with its XSS and restore/opener limitations stated. Scope bearer attachment to the existing API, clear credentials on logout/expiry/401, and do not persist passwords or wallet data. A change to cookie-based authentication is outside this candidate.

Sources: <https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html>; <https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html>.
