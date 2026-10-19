# Direct local development

Run PayNest directly on your machine with JDK **25**, Maven Wrapper **3.10.0**,
and PostgreSQL **18.6**. Run commands from the repository root. PostgreSQL is
developer-managed: install and start it before starting PayNest. The first
Wrapper/build invocation downloads Maven and dependencies and needs internet access.

## 1. Check tools and create separate databases

In PowerShell, point `JAVA_HOME` at your JDK 25 installation (not its `bin`
directory), then check the tools:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
# Adjust this if PostgreSQL was installed elsewhere.
$env:Path = 'C:\Program Files\PostgreSQL\18\bin;' + $env:Path
java -version
.\mvnw.cmd -version
psql --version
psql -h localhost -p 5432 -U postgres -d postgres -W
```

Expect Java 25, Apache Maven 3.10.0, and PostgreSQL 18.6. `-W` prompts for the
existing PostgreSQL administrator password; passwords never belong in command
arguments or committed files. In the `psql` session, create roles/databases once:

```sql
CREATE ROLE paynest_local LOGIN;
\password paynest_local
CREATE DATABASE paynest OWNER paynest_local;
REVOKE CONNECT ON DATABASE paynest FROM PUBLIC;
GRANT CONNECT ON DATABASE paynest TO paynest_local;

CREATE ROLE paynest_test LOGIN;
\password paynest_test
CREATE DATABASE paynest_test OWNER paynest_test;
REVOKE CONNECT ON DATABASE paynest_test FROM PUBLIC;
GRANT CONNECT ON DATABASE paynest_test TO paynest_test;
\q
```

Each `\password` prompts twice for a new password. Use different passwords and
roles for the two databases. Ownership lets Flyway create its `paynest` schema,
tables, and migration history. The application roles need no superuser,
`CREATEDB`, or `CREATEROLE` privileges. Existing databases/roles must be inspected
and reused with their actual credentials; do not drop them or reset their data.
Run the creation block only against a new setup. The test database is disposable
and must never hold development data. Do not grant the test role access to the
development database.

On Unix, the equivalent tool setup and database entry are:

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:/path/to/postgresql-18/bin:$PATH"
java -version
./mvnw -version
psql --version
psql -h localhost -p 5432 -U postgres -d postgres -W
```

Use the same SQL block. If the checkout lacks executable permissions, run
`sh ./mvnw` in place of `./mvnw` in the Unix commands below.

## 2. Export configuration in your shell

`.env.example` lists the existing nine configuration variables. A `.env` file is
**not loaded automatically** by Spring Boot or the Maven Wrapper. The following
exports apply to the current shell and its child processes; set them in every
terminal that starts the app or tests. Keep real credentials out of Git.

| Variable | Purpose / local value |
| --- | --- |
| `PAYNEST_DB_URL` | Development JDBC URL: `jdbc:postgresql://localhost:5432/paynest` |
| `PAYNEST_DB_USERNAME` | Development owner: `paynest_local` |
| `PAYNEST_DB_PASSWORD` | Prompted development role password |
| `PAYNEST_TEST_DB_URL` | Disposable test JDBC URL: `jdbc:postgresql://localhost:5432/paynest_test` |
| `PAYNEST_TEST_DB_USERNAME` | Test owner: `paynest_test` |
| `PAYNEST_TEST_DB_PASSWORD` | Prompted test role password |
| `PAYNEST_JWT_SECRET` | Base64 encoding of at least 32 random bytes |
| `PAYNEST_JWT_TTL` | Token lifetime; default `60m` |
| `PAYNEST_DB_LOCK_TIMEOUT` | PostgreSQL lock wait; default `5s`, no automatic retry |

PowerShell (compatible with Windows PowerShell 5.1 as well as PowerShell 7):

```powershell
$env:PAYNEST_DB_URL = 'jdbc:postgresql://localhost:5432/paynest'
$env:PAYNEST_DB_USERNAME = 'paynest_local'
$env:PAYNEST_DB_PASSWORD = (New-Object System.Net.NetworkCredential('', (Read-Host 'Development database password' -AsSecureString))).Password
$env:PAYNEST_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/paynest_test'
$env:PAYNEST_TEST_DB_USERNAME = 'paynest_test'
$env:PAYNEST_TEST_DB_PASSWORD = (New-Object System.Net.NetworkCredential('', (Read-Host 'Test database password' -AsSecureString))).Password
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRandom.GetBytes($jwtBytes) } finally { $jwtRandom.Dispose() }
$env:PAYNEST_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:PAYNEST_JWT_TTL = '60m'
$env:PAYNEST_DB_LOCK_TIMEOUT = '5s'
```

Unix equivalent (Bash, with `openssl` installed):

```bash
export PAYNEST_DB_URL=jdbc:postgresql://localhost:5432/paynest
export PAYNEST_DB_USERNAME=paynest_local
read -r -s -p 'Development database password: ' PAYNEST_DB_PASSWORD; printf '\n'
export PAYNEST_DB_PASSWORD
export PAYNEST_TEST_DB_URL=jdbc:postgresql://localhost:5432/paynest_test
export PAYNEST_TEST_DB_USERNAME=paynest_test
read -r -s -p 'Test database password: ' PAYNEST_TEST_DB_PASSWORD; printf '\n'
export PAYNEST_TEST_DB_PASSWORD
export PAYNEST_JWT_SECRET="$(openssl rand -base64 32)"
export PAYNEST_JWT_TTL=60m
export PAYNEST_DB_LOCK_TIMEOUT=5s
```

Environment variables necessarily reach the running process as plaintext. Avoid
printing them, logging request tokens, or saving shell transcripts with secrets.
Keep the signing secret stable across restarts when you want existing JWTs to
remain valid. The test profile has a test-only signing fallback; local startup
requires the exported random secret.

## 3. Start the application and inspect documentation

```powershell
.\mvnw.cmd '-Dspring-boot.run.profiles=local' spring-boot:run
```

Unix: `./mvnw -Dspring-boot.run.profiles=local spring-boot:run`.

On an **empty development database**, Flyway creates the `paynest` schema and
applies V1 (users), V2 (wallets), and V3 (transactions). Hibernate then validates
the mapped schema (`ddl-auto=validate`). Existing development data is preserved
on subsequent starts. Wait for the application `Started PayNestApplication`
message and the embedded Tomcat startup message on port 8080. Failures before
these messages mean startup has not completed.

Public documentation, requiring no bearer token:

- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>
- Swagger UI: <http://localhost:8080/swagger-ui/index.html>

Swagger exposes exactly the seven product operations below. Its Authorize
dialog accepts the access token from signin; protect that token as a credential.

## 4. Exercise all seven product operations

Leave the server running and open a second PowerShell terminal at the repository
root. These accounts and passwords are invented demonstration values. Use fresh
email addresses on subsequent runs, and update the SQL fixture to match the
sender. Reusing an existing email returns `409 EMAIL_ALREADY_EXISTS`; do not
delete development data to repeat the demo. Balances below assume new wallets
and exactly one funding and transfer execution.

Initialize the request inputs:

```powershell
$base = 'http://localhost:8080'
$senderEmail = 'ada.payne@example.test'
$recipientEmail = 'grace.hopper@example.test'
$demoPassword = 'local-demo-placeholder-password'
```

1. `POST /api/auth/signup`: register **both** users. Each returns `201`, `user`,
   and exactly one wallet with `balance: 0.00`, `currency: USD`, and `createdAt`.

```powershell
$senderSignup = Invoke-RestMethod -Method Post -Uri "$base/api/auth/signup" -ContentType 'application/json' -Body (@{name='Ada Payne'; email=$senderEmail; password=$demoPassword} | ConvertTo-Json)
$recipientSignup = Invoke-RestMethod -Method Post -Uri "$base/api/auth/signup" -ContentType 'application/json' -Body (@{name='Grace Hopper'; email=$recipientEmail; password=$demoPassword} | ConvertTo-Json)
$senderSignup.wallet
$recipientSignup.wallet
```

2. `POST /api/auth/signin`: sign in as each user. Each returns `200`,
   `accessToken`, `tokenType: Bearer`, and `expiresAt`. Keep tokens in variables.

```powershell
$senderLogin = Invoke-RestMethod -Method Post -Uri "$base/api/auth/signin" -ContentType 'application/json' -Body (@{email=$senderEmail; password=$demoPassword} | ConvertTo-Json)
$recipientLogin = Invoke-RestMethod -Method Post -Uri "$base/api/auth/signin" -ContentType 'application/json' -Body (@{email=$recipientEmail; password=$demoPassword} | ConvertTo-Json)
$senderHeaders = @{Authorization="Bearer $($senderLogin.accessToken)"}
$recipientHeaders = @{Authorization="Bearer $($recipientLogin.accessToken)"}
```

3. `GET /api/users/me`: expect `200` with Ada's user profile.

```powershell
Invoke-RestMethod -Uri "$base/api/users/me" -Headers $senderHeaders
```

4. `GET /api/wallet`: expect `200` and zero USD for both new wallets.

```powershell
Invoke-RestMethod -Uri "$base/api/wallet" -Headers $senderHeaders
Invoke-RestMethod -Uri "$base/api/wallet" -Headers $recipientHeaders
```

**Manually fund only the sender before transferring.** Open
`scripts/dev/fund-wallet.sql` and set its three editable declarations:
`expected_database` to `paynest`, `normalized_email` to the exact stored sender
email, and `funding_amount_text` to `100.00`. Use a lowercase address with no
surrounding whitespace, as returned by signup. Do not commit personal accounts.
Run the whole file; `-W` prompts for the development role password:

```powershell
psql -h localhost -p 5432 -U paynest_local -d paynest -W -v ON_ERROR_STOP=1 -f scripts/dev/fund-wallet.sql
```

Unix uses the same `psql` command. A database GUI can also execute the whole
file against the explicitly selected database. The fixture allows only
`paynest` or `paynest_test` and requires the actual connection to match the edited
database declaration. It locks and adds to one existing USD wallet atomically.
Missing wallets, zero/negative amounts, more than two fractional digits (even
`1.000`), non-finite numbers, and overflow fail without partial writes. It
preserves unrelated data and creates **no Transaction**; it is neither a
migration nor an application endpoint. It never runs automatically. Repeating
the fixture adds funds again. Recheck wallets: sender `100.00`, recipient `0.00`;
`GET /api/transactions` still has `totalElements: 0` for both.

5. `POST /api/wallet/transfers`: expect `201`, `transaction.status: COMPLETED`,
   amount `25.50`, direction `SENT`, recipient counterparty, and
   `balanceAfter: 74.50`. Amounts are exact USD decimals with at most two
   fractional digits and maximum `99999999999999999.99`.

```powershell
$transfer = Invoke-RestMethod -Method Post -Uri "$base/api/wallet/transfers" -Headers $senderHeaders -ContentType 'application/json' -Body (@{recipientEmail=$recipientEmail; amount=[decimal]25.50} | ConvertTo-Json)
$transactionId = $transfer.transaction.id
$transfer | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "$base/api/wallet" -Headers $senderHeaders
Invoke-RestMethod -Uri "$base/api/wallet" -Headers $recipientHeaders
```

Balances must now be `74.50` and `25.50` respectively.

6. `GET /api/transactions`: both return `200` and the same transaction ID in
   `content`, with `SENT` for Ada and `RECEIVED` for Grace. Defaults are
   `page=0`, `size=20`; supported bounds are `page >= 0`, `1 <= size <= 100`.

```powershell
Invoke-RestMethod -Uri "$base/api/transactions?page=0&size=20" -Headers $senderHeaders | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "$base/api/transactions?page=0&size=20" -Headers $recipientHeaders | ConvertTo-Json -Depth 5
```

7. `GET /api/transactions/{id}`: use the positive ID returned by the transfer.
   Both participants get `200`, the same sender/recipient, amount, USD currency,
   `COMPLETED` status, and their respective direction. Other users cannot read it.

```powershell
Invoke-RestMethod -Uri "$base/api/transactions/$transactionId" -Headers $senderHeaders | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "$base/api/transactions/$transactionId" -Headers $recipientHeaders | ConvertTo-Json -Depth 5
```

For a Unix HTTP client, use `curl` with the same JSON field names/paths. This
Bash sequence uses `jq` to capture tokens/IDs without printing credentials:

```bash
base=http://localhost:8080
sender=ada.payne@example.test
recipient=grace.hopper@example.test
password=local-demo-placeholder-password
for email in "$sender" "$recipient"; do
  curl --fail-with-body -sS -H 'Content-Type: application/json' \
    -d "{\"name\":\"Demo User\",\"email\":\"$email\",\"password\":\"$password\"}" "$base/api/auth/signup"
done
sender_token=$(curl --fail-with-body -sS -H 'Content-Type: application/json' -d "{\"email\":\"$sender\",\"password\":\"$password\"}" "$base/api/auth/signin" | jq -er .accessToken)
recipient_token=$(curl --fail-with-body -sS -H 'Content-Type: application/json' -d "{\"email\":\"$recipient\",\"password\":\"$password\"}" "$base/api/auth/signin" | jq -er .accessToken)
curl --fail-with-body -sS -H "Authorization: Bearer $sender_token" "$base/api/users/me"
for token in "$sender_token" "$recipient_token"; do
  curl --fail-with-body -sS -H "Authorization: Bearer $token" "$base/api/wallet"
done
# Edit the fixture declarations as above, then fund the sender exactly once.
psql -h localhost -p 5432 -U paynest_local -d paynest -W -v ON_ERROR_STOP=1 -f scripts/dev/fund-wallet.sql
transfer=$(curl --fail-with-body -sS -H "Authorization: Bearer $sender_token" -H 'Content-Type: application/json' -d "{\"recipientEmail\":\"$recipient\",\"amount\":25.50}" "$base/api/wallet/transfers")
transaction_id=$(printf '%s' "$transfer" | jq -er .transaction.id)
printf '%s\n' "$transfer"
for token in "$sender_token" "$recipient_token"; do
  curl --fail-with-body -sS -H "Authorization: Bearer $token" "$base/api/wallet"
  curl --fail-with-body -sS -H "Authorization: Bearer $token" "$base/api/transactions?page=0&size=20"
  curl --fail-with-body -sS -H "Authorization: Bearer $token" "$base/api/transactions/$transaction_id"
done
```

## 5. Run isolated PostgreSQL verification

Stop any other test process first. Configure all three `PAYNEST_TEST_DB_*`
variables in the test shell. First confirm the actual connection using the test
role (or your existing test role name) and prompted password:

```powershell
psql -h localhost -p 5432 -U paynest_test -d paynest_test -W -c 'select current_database(), version();'
.\mvnw.cmd '-Dtest=LocalStartupSmokeIntegrationTests,TestDatabaseSupportTests' test
.\mvnw.cmd clean verify
```

Unix: `./mvnw '-Dtest=LocalStartupSmokeIntegrationTests,TestDatabaseSupportTests' test`
and `./mvnw clean verify`.

Existing Spring integration tests select the `test` profile themselves. Its
Hikari connection-init SQL checks `current_database()` on every new physical
connection and rejects any database other than exactly `paynest_test`, before
Flyway or Hibernate can use that connection. Do not change test variables
to a development URL. Tests delete application test data and reset the application
schema in the disposable `paynest_test` database. They run sequentially; do not
run multiple Maven test processes against this database. The focused startup
test queries `current_database()` **before application bootstrap or schema reset**
and refuses anything other than exactly `paynest_test`. It starts the actual
`local` profile with that verified disposable datasource pinned ahead of ambient
configuration and a freshly generated in-memory JWT signing secret. It proves an
absent application schema migrates V1-V3, Hibernate validates, a real HTTP server starts,
the delivered funding SQL rejects unsafe inputs, all seven operations work,
documentation is public, and the context/server closes. This is fresh-start
proof for the **local profile against the test database**; it does not establish
that an existing development database was empty or that development-database
startup was observed. It also executes the configured test-profile connection
guard's success and refusal branches on the disposable database.

`clean verify` also checks existing migration/schema, endpoint, and entity
contracts and builds `target/paynest-0.0.1-SNAPSHOT.jar`. With the development
environment exported, the packaged startup alternative is:

```powershell
java -jar target/paynest-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Unix uses the same `java -jar` command. Avoid `-DskipTests` when verifying delivery.

## 6. Shut down

Press **Ctrl+C** in the terminal running Maven or `java -jar`, then wait for
Tomcat, Hibernate's entity manager, and Hikari's pool to close and for the shell
prompt to return. Automated smoke verification closes its own server/context.
The PostgreSQL service remains developer-managed; stop it through your normal
service manager only when no other local application needs it. Close demo shells
to discard their environment variables and token variables.

## Troubleshooting

- **Wrong Java/Maven:** `java -version` and `.\mvnw.cmd -version` must both show
  JDK 25. Correct `JAVA_HOME`/`PATH` in the same shell; use the committed Wrapper
  instead of a globally installed `mvn`. If first download fails, check internet,
  proxy, and Maven repository access.
- **`psql` not found:** add PostgreSQL's `bin` directory to `PATH`, or call
  `& 'C:\Program Files\PostgreSQL\18\bin\psql.exe'` in PowerShell.
- **JWT startup failure:** export `PAYNEST_JWT_SECRET` in the startup shell using
  the random-byte recipe. A literal placeholder, malformed Base64, or fewer
  than 32 decoded bytes is invalid. After secret changes or token expiry, sign
  in again; never include token/secret values in a bug report.
- **Connection refused/authentication failed:** verify PostgreSQL is running
  on the configured host/port; test the matching role/database with `psql -W`.
  Check `pg_hba.conf` permits password authentication for the intended local
  connection and recheck the exported password. Do not put the admin role in
  application configuration.
- **Schema permission denied:** check database ownership (`\l` in `psql`) and
  that the application role owns the database/application schema. Have your
  local administrator correct the specific grants/ownership; do not drop
  existing schemas, disable Hibernate validation, or edit applied migrations.
- **Port conflict:** free port 8080 by stopping the known owning application, or
  run `.\mvnw.cmd '-Dspring-boot.run.profiles=local' '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run`
  and change all demo/documentation URLs to 8081. PostgreSQL port changes must
  be reflected in both JDBC URLs and `psql -p` arguments. The smoke server uses
  an available random port.
- **HTTP errors:** missing/expired JWT gives `401`; reused signup email gives
  `409`; an unfunded transfer gives `422 INSUFFICIENT_BALANCE`. Follow the
  funding step only for your intended wallet, then retry the transfer once.
  Errors use `timestamp`, `status`, `error`, `message`, `path`, and optional
  `fieldErrors`. Check these safe fields without sharing credentials.
- **Test database guard refusal:** correct the test URL/user/password and verify
  `select current_database()` returns exactly `paynest_test`. Never weaken the
  guard or rename a development database to make tests run.
