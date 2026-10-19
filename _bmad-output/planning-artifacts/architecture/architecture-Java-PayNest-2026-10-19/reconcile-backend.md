# Backend reconciliation

Reviewed 2026-10-19 against the read-only [backend parent spine](../architecture-Java-PayNest-2026-10-06/ARCHITECTURE-SPINE.md), current Java controllers/DTOs, security configuration/filter, registration/transfer services, persistence/configuration, Maven build and local-development runbook.

Result: no unresolved backend scope or contract contradiction found. This is a source/document reconciliation; it does not claim that the proposed frontend or packaged integration has been implemented or tested.

## Inherited boundary

| Check | Evidence and reconciliation |
|---|---|
| One deployment/database | Parent AD-1 and frontend AD-21 retain one executable Spring Boot JAR and one PostgreSQL datasource per environment. `pom.xml` currently packages only the backend; the proposed opt-in browser asset copy adds build work, not a runtime service. Two local development processes do not create a second deployment unit. |
| Three entities | The only `@Entity` classes are User, Wallet and Transaction. Frontend AD-13 introduces no entity, table or persistence migration. |
| Local ACID | RegistrationService has one `@Transactional` registration. TransferService has one `READ_COMMITTED` transaction; WalletRepository locks wallets with pessimistic write in ascending ID order. AD-18 leaves the server authoritative and introduces no distributed state or retry. |
| Verification database | `application-test.yml` rejects physical connections outside exactly `paynest_test`; TestDatabaseSupport checks the actual database before cleanup/reset. AD-22 preserves `PAYNEST_TEST_DB_*`, manual/test-only funding, and this guard. Real-backend browser smoke still needs explicit test-profile/datasource wiring during implementation. |
| Delivery and exclusions | AD-13/21/22 preserve direct local startup, external secrets, no Docker/H2/container substitute, no funding endpoint, no refresh/logout/session/role API and no new product operation. |

## Seven-operation wire contract

The current controllers and DTO records are compatible with AD-9/15/19 and the client-model convention. Braces below describe object fields, not wrappers added to the API.

| Method/path | Request | Success body/status |
|---|---|---|
| POST `/api/auth/signup` | `{name,email,password}` | 201 `{user,wallet}` |
| POST `/api/auth/signin` | `{email,password}` | 200 `{accessToken,tokenType,expiresAt}`; tokenType is `Bearer` |
| GET `/api/users/me` | None | 200 `{id,name,email,createdAt}` |
| GET `/api/wallet` | None | 200 `{balance,currency,createdAt}`; no Wallet ID |
| POST `/api/wallet/transfers` | `{recipientEmail,amount}` | 201 `{transaction,balanceAfter}`; transaction is `{id,direction,counterparty,amount,currency,status,createdAt}` |
| GET `/api/transactions` | Query `page`, `size` | 200 `{content,page,size,totalElements,totalPages,last}`; each item has the transfer transaction summary shape |
| GET `/api/transactions/{id}` | Positive BIGINT path text | 200 `{id,direction,sender,recipient,amount,currency,status,createdAt}` |

Participants/counterparties contain name and email. Currency is USD, persisted status is COMPLETED, and time is Instant/UTC on the wire. Transfer direction is SENT; history/detail are viewer-relative SENT or RECEIVED. History defaults to page 0, size 20, with the server maximum 100. The fixed frontend size 20 is compatible.

Money DTO fields and transfer input remain BigDecimal JSON numbers; IDs are Long and totalElements is long, also JSON numbers. AD-15's text-first lossless decoding and numeric-token serialization preserve these wire types while keeping browser models exact. Its maximum money and int64 bounds match NUMERIC(19,2)/BIGINT. TransferService rejects input scale over two, including `25.500`; canonicalizing only after entry validation preserves this rule. No backend stringification change is required.

ApiError is `{timestamp,status,error,message,path,fieldErrors?}` with fieldErrors a string-valued object. The draft retains the existing stable vocabulary, participant-only detail authorization and the common missing/inaccessible receipt presentation. Signup code counts Unicode code points and preserves passwords; AD-19 requires compatible validation rather than applying signup limits to signin.

## Security and packaging integration

SecurityConfig is stateless, permits the two POST auth operations and existing documentation resources, authenticates exactly the five protected operation patterns, and denies every other request. JwtAuthenticationFilter only processes those five patterns and resolves the JWT subject against the persisted User. AD-17 matches this boundary; it requires no filter expansion, server session, CORS expansion or new endpoint.

Current default-deny intentionally blocks new HTML/asset requests. AD-21 explicitly authorizes the minimal implementation exception: public GET HTML navigation and generated UI resources, with reserved API/documentation/operational/error/asset namespaces excluded from SPA fallback. This is planned integration work, not an inconsistency. Preserve the existing API method/path matchers and JSON 401/403 behavior, including unknown paths/methods, while adding it. The draft already requires packaged-JAR checks for these cases and forbids blanket public `/**` matching.

FE-AQ-1 remains intentionally pending: sessionStorage exposes the browser credential to same-origin injected scripts. This browser custody proposal does not introduce server-side session storage or alter the parent's stateless bearer model. Its residual risk must be accepted before AD-16 is finalized; cookie/BFF or server-session changes are outside the authorized scope.

No parent or backend source was changed.
