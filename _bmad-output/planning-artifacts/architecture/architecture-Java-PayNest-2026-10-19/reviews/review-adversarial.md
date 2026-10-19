# Adversarial seam review

Verdict: tighten three shared seams before independently implementing the frontend stories. FE-AQ-1 remains the explicitly pending risk acceptance; this review does not resolve it or change draft status.

Read the complete architecture spine and reviewer gate, approved UX spines/references from the preceding reconciliation, and narrowly inspected ApiError, TransferController and GlobalExceptionHandler to check error-classification evidence. No optional UX rubric/accessibility review was run.

## High — AD-14 permits a bootstrap-time dependency cycle

Two independently built units can obey every dependency rule and fail together:

- Session story injects PayNestApi and starts stored-session bootstrap in its constructor. AD-14 expressly lets SessionService coordinate bootstrap through PayNestApi; AD-16 requires the currentUser verification. Neither fixes initialization timing.
- HTTP story supplies a functional interceptor which calls `inject(SessionService)` when that protected currentUser request is dispatched, exactly as AD-14 prescribes.

The request occurs while SessionService is still being constructed, so request-time injection re-enters its unresolved provider and can produce Angular's circular-DI failure. Lazy interceptors alone do not close this seam.

**Exact tightening:** require SessionService construction to be side-effect-free; initiate restoration through one application initializer/bootstrap coordinator only after SessionService is fully resolved. Register the root HttpClient/interceptor chain once. No constructor/eager field initializer starts HTTP. Keep the existing dependency ownership; no new service hierarchy is required.

## Medium — AD-14/15 name operations but leave their public TypeScript contract open

The canonical numeric scalar rules are strong, but do not fix the interface that separate core and page stories consume:

- Core story exports readonly validated models with string money/IDs and `history(query: {page:number;size:number}): Observable<HistoryPage>`. It can validly return a decoded body from transfer after internally requiring status 201, and represent timestamps as validated ISO strings.
- Page story expects `history(page:number,size:number)`, transfer as an observed response exposing status/body, and timestamps as Date objects. It imports the core-owned models rather than duplicating them, and otherwise follows every state, precision and screen rule.

Both interpretations are permitted by the spine, but their calls/consumers do not type-check together. The same gap covers ApiCodec's failure shape and SessionService's state/generation/transfer-gate protocol; the table defers names while claiming model types cannot change, although those types are not fully specified.

**Exact tightening:** add one compact shared contract block (or a specifically named, governing contract companion) before splitting stories: the seven PayNestApi parameter/Observable-return signatures; exported DTO fields/nullability and internal timestamp representation; one discriminated client-error type retaining transport status and validated ApiError where available; and the small SessionService state/snapshot, initialization, generation, and begin/settle-transfer surface. Specify where HTTP status is consumed so features cannot expect an envelope that the client strips. This is a contract, not extra facade classes or a framework.

## High — AD-18 does not define the boundary between definite rejection and classified 5xx

Two compliant transfer units can disagree on the same response:

- Codec/transport story regards a fully decoded `500 / TRANSACTION_FAILED` as a recognized backend rejection: TransferController explicitly documents 500 as rolled back and GlobalExceptionHandler emits that stable code. It clears the recovery marker as a known rejection.
- Transfer/recovery story treats every 5xx as unknown, retaining the marker, because the response can originate after dispatch and a generic exception response cannot establish what committed. It only accepts the known validation/business rejection combinations as definite.

AD-18 explicitly says *unclassified* 5xx is unknown but supplies no common classification table, so both readings are possible. The difference changes whether the user is told it is safe to correct/resend. DTO validity by itself proves the response shape, not transaction rollback.

**Exact tightening:** define one shared outcome classifier, owned by the API/codec boundary or a named pure core function. Adopt the conservative table: validated 201+COMPLETED is success; validated 400/VALIDATION_ERROR, 404/USER_NOT_FOUND or WALLET_NOT_FOUND, 409/CONFLICT, and 422/SELF_TRANSFER_NOT_ALLOWED or INSUFFICIENT_BALANCE are definite rejection; protected 401 follows AD-17's clearing and generic prior-submission warning; every 5xx (including TRANSACTION_FAILED), unexpected status/code pairing, network/cancellation-after-dispatch, malformed body or DTO contract failure is unknown. If a particular 5xx is instead intended to prove rollback, bind that exception to an independently established backend guarantee rather than leaving each feature to infer one.

## Other seams checked

No additional finding from the numeric canonicalization/range rules, nonce-scoped marker privacy, generation-scoped old-session responses, fixed history envelope, or packaged UI/API namespace split. The marker must precede dispatch and storage failure blocks dispatch; old-session responses cannot clear a new session's marker. AD-19 fixes page commit timing and independent read states. AD-21 explicitly reserves API/docs/error/asset namespaces before UI fallback, including unknown paths and methods, and AD-22 requires packaged-JAR verification. These are concrete constraints rather than missing decisions. Runtime tests remain required and this review is not proof of an implementation.

## Targeted closure recheck — 19 October 2026

**PASS.** Rechecked the three identified seams against the amended spine, without restarting the review.

| Finding | Closure evidence | Status |
|---|---|---|
| Bootstrap DI cycle | AD-14 requires side-effect-free service construction and an explicit initializer calling restore only after dependencies are constructed. | Closed |
| Incompatible shared contracts | AD-14 establishes the core contract before page stories; the shared contract seed fixes seven cold-Observable calls/body emissions, ApiFailure error-channel shape, DTO ownership, readonly session Signals and session operation protocol. Pages consume these exports. | Closed |
| Ambiguous 5xx classification | AD-18 now supplies one explicit result table; every 5xx is unknown, while specified status/code pairs are rejected. Unexpected/malformed responses also remain unknown. | Closed |

The amended per-attempt ticket, active-ticket/generation check and idempotent settlement also prevent an older same-login result from clearing a newer marker. AD-19 separately rejects superseded same-session reads. These additions are consistent with the reviewed ownership and recovery constraints.

No remaining gap from this targeted recheck. FE-AQ-1 remains intentionally pending user risk acceptance, and the architecture remains draft. This is contract reconciliation, not runtime verification or implementation authorization.
