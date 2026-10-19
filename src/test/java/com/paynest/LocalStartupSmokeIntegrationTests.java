package com.paynest;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paynest.support.TestDatabaseSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocalStartupSmokeIntegrationTests {

    private static final String SENDER = "ada.payne@example.test";
    private static final String RECIPIENT = "grace.hopper@example.test";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void fixtureReplacesEachDeclarationEvenAfterItsEditableValuesChange() {
        String edited = "expected_database text := NULL;\n"
                + "normalized_email text := 'repeat.demo@example.test';\n"
                + "funding_amount_text text := '52.10';\n";
        assertThat(fixture(edited, "paynest_test", SENDER, "100.00")).isEqualTo(
                "expected_database text := 'paynest_test';\n"
                        + "normalized_email text := 'ada.payne@example.test';\n"
                        + "funding_amount_text text := '100.00';\n");
        assertThatThrownBy(() -> fixture(edited + "expected_database text := 'duplicate';",
                "paynest_test", SENDER, "100.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnotherDatabaseNameBeforeResetOrApplicationBootstrap() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("paynest");
        @SuppressWarnings("unchecked")
        Supplier<ConfigurableApplicationContext> bootstrap = mock(Supplier.class);

        assertThatThrownBy(() -> freshApplication(jdbc, bootstrap))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("paynest_test");
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
                .execute(org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(bootstrap);
    }

    @Test
    void freshTestDatabaseStartsAndCompletesTheDocumentedJourneyAndClosesItsServer() throws Exception {
        DriverManagerDataSource guardedDataSource = new DriverManagerDataSource(
                TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_URL"),
                TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_USERNAME"),
                TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(guardedDataSource);
        byte[] signingBytes = new byte[32];
        new SecureRandom().nextBytes(signingBytes);
        ConfigurableApplicationContext context = freshApplication(jdbc,
                () -> new SpringApplicationBuilder(PayNestApplication.class)
                        .initializers(application -> application.getEnvironment().getPropertySources()
                                .addFirst(new MapPropertySource("guardedSmokeDatabase", Map.of(
                                        "spring.datasource.url", guardedDataSource.getUrl(),
                                        "spring.datasource.username", guardedDataSource.getUsername(),
                                        "spring.datasource.password", guardedDataSource.getPassword(),
                                        "paynest.jwt.secret", Base64.getEncoder().encodeToString(signingBytes)))))
                        .profiles("local").run("--server.port=0"));
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        URI base = URI.create("http://localhost:" + port);
        try (context; HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            assertThat(context.getBean(JdbcTemplate.class)
                    .queryForObject("select current_database()", String.class)).isEqualTo("paynest_test");
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("local");
            assertThat(jdbc.queryForList("select version from paynest.flyway_schema_history "
                    + "where success and version is not null order by installed_rank", String.class))
                    .containsExactly("1", "2", "3");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                    .isEqualTo("validate");
            assertThat(jdbc.queryForObject("select count(*) from paynest.users", Integer.class)).isZero();

            for (String email : List.of(SENDER, RECIPIENT)) {
                JsonNode signup = request(client, base, "POST", "/api/auth/signup", null,
                        Map.of("name", email.equals(SENDER) ? "Ada Payne" : "Grace Hopper",
                                "email", email, "password", "smoke-placeholder-password"), 201);
                assertMoney(signup.at("/wallet/balance"), "0.00");
                assertThat(signup.at("/wallet/currency").asText()).isEqualTo("USD");
            }
            assertThat(jdbc.queryForObject("select count(*) from paynest.wallets", Integer.class)).isEqualTo(2);
            String senderToken = signin(client, base, SENDER);
            String recipientToken = signin(client, base, RECIPIENT);
            assertThat(request(client, base, "GET", "/api/users/me", senderToken, null, 200)
                    .path("email").asText()).isEqualTo(SENDER);
            assertMoney(request(client, base, "GET", "/api/wallet", senderToken, null, 200)
                    .path("balance"), "0.00");

            String fixture = Files.readString(Path.of("scripts/dev/fund-wallet.sql"));
            fundsWithoutOtherWrites(jdbc, fixture(fixture, "paynest_test", SENDER, "100.00"), SENDER, "100.00");
            assertThat(balance(jdbc, SENDER)).isEqualByComparingTo("100.00");
            assertThat(balance(jdbc, RECIPIENT)).isEqualByComparingTo("0.00");
            assertThat(transactionCount(jdbc)).isZero();

            JsonNode transfer = request(client, base, "POST", "/api/wallet/transfers", senderToken,
                    Map.of("recipientEmail", RECIPIENT, "amount", new BigDecimal("25.50")), 201);
            long id = transfer.at("/transaction/id").asLong();
            assertThat(id).isPositive();
            assertThat(transfer.at("/transaction/status").asText()).isEqualTo("COMPLETED");
            assertMoney(transfer.at("/transaction/amount"), "25.50");
            assertMoney(transfer.path("balanceAfter"), "74.50");
            for (String token : List.of(senderToken, recipientToken)) {
                boolean sender = token.equals(senderToken);
                assertMoney(request(client, base, "GET", "/api/wallet", token, null, 200)
                        .path("balance"), sender ? "74.50" : "25.50");
                JsonNode history = request(client, base, "GET", "/api/transactions", token, null, 200);
                assertThat(history.path("totalElements").asLong()).isOne();
                assertThat(history.at("/content/0/id").asLong()).isEqualTo(id);
                assertThat(history.at("/content/0/direction").asText())
                        .isEqualTo(sender ? "SENT" : "RECEIVED");
                JsonNode detail = request(client, base, "GET", "/api/transactions/" + id, token, null, 200);
                assertThat(detail.path("id").asLong()).isEqualTo(id);
                assertThat(detail.at("/sender/email").asText()).isEqualTo(SENDER);
                assertThat(detail.at("/recipient/email").asText()).isEqualTo(RECIPIENT);
                assertThat(detail.path("direction").asText()).isEqualTo(sender ? "SENT" : "RECEIVED");
                assertMoney(detail.path("amount"), "25.50");
            }
            // Rejections must preserve the pre-existing transfer and all wallet data.
            for (String invalid : List.of("-1", "1.001", "1.000", "NaN", "Infinity")) {
                rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", SENDER, invalid), "22023");
            }
            for (String invalid : List.of("0", "100000000000000000.00", "99999999999999999.99")) {
                rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", SENDER, invalid), "22003");
            }
            rejectsWithoutWrites(jdbc, fixture(fixture, "paynest", SENDER, "100.00"), "3D000");
            rejectsWithoutWrites(jdbc, replaceDeclaration(fixture(fixture, "paynest_test", SENDER, "100.00"),
                    "expected_database", "NULL"), "3D000");
            rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", "missing@example.test", "100.00"), "P0002");
            jdbc.update("insert into paynest.users(name, email, password_hash, created_at) "
                    + "values ('Walletless Demo', 'walletless@example.test', 'test-only-placeholder', current_timestamp)");
            rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", "walletless@example.test", "100.00"), "P0002");
            rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", " ADA@example.test ", "100.00"), "22023");
            // A second valid credit is additive and preserves existing history.
            fundsWithoutOtherWrites(jdbc, fixture(fixture, "paynest_test", SENDER, "0.01"), SENDER, "0.01");
            assertThat(balance(jdbc, SENDER)).isEqualByComparingTo("74.51");
            assertThat(balance(jdbc, RECIPIENT)).isEqualByComparingTo("25.50");
            assertThat(transactionCount(jdbc)).isOne();

            String maximumEmail = "maximum.fixture@example.test";
            JsonNode maximumSignup = request(client, base, "POST", "/api/auth/signup", null,
                    Map.of("name", "Maximum Fixture", "email", maximumEmail,
                            "password", "smoke-placeholder-password"), 201);
            assertMoney(maximumSignup.at("/wallet/balance"), "0.00");
            fundsWithoutOtherWrites(jdbc, fixture(fixture, "paynest_test", maximumEmail,
                    "99999999999999999.99"), maximumEmail, "99999999999999999.99");
            assertThat(balance(jdbc, maximumEmail)).isEqualByComparingTo("99999999999999999.99");
            rejectsWithoutWrites(jdbc, fixture(fixture, "paynest_test", maximumEmail, "0.01"), "22003");

            String connectionGuard = (String) new YamlPropertySourceLoader()
                    .load("testGuard", new ClassPathResource("application-test.yml")).getFirst()
                    .getProperty("spring.datasource.hikari.connection-init-sql");
            assertThat(connectionGuard).isNotBlank();
            DatabaseSnapshot beforeGuard = snapshot(jdbc);
            jdbc.execute(connectionGuard);
            assertThat(snapshot(jdbc)).isEqualTo(beforeGuard);
            // Exercise the exact configured statement's refusal branch on the same
            // verified disposable connection, without accessing any other database.
            rejectsWithoutWrites(jdbc, connectionGuard.replace("'paynest_test'", "'refused_database'"), "3D000");

            assertThat(request(client, base, "GET", "/v3/api-docs", null, null, 200)
                    .path("paths").size()).isEqualTo(7);
            HttpResponse<String> swagger = client.send(HttpRequest.newBuilder(
                    base.resolve("/swagger-ui/index.html")).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(swagger.statusCode()).isEqualTo(200);
            assertThat(swagger.body()).contains("Swagger UI");
        }
        assertThat(context.isActive()).isFalse();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertThatThrownBy(() -> client.send(HttpRequest.newBuilder(base.resolve("/v3/api-docs"))
                            .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding()))
                    .isInstanceOf(java.io.IOException.class);
        }
    }

    private static ConfigurableApplicationContext freshApplication(
            JdbcOperations jdbc, Supplier<ConfigurableApplicationContext> bootstrap) {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbc);
        TestDatabaseSupport.resetPayNestSchema(jdbc);
        assertThat(jdbc.queryForObject("select to_regnamespace('paynest')::text", String.class)).isNull();
        return bootstrap.get();
    }

    private String signin(HttpClient client, URI base, String email) throws Exception {
        JsonNode token = request(client, base, "POST", "/api/auth/signin", null,
                Map.of("email", email, "password", "smoke-placeholder-password"), 200);
        assertThat(token.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(token.path("accessToken").asText()).isNotBlank();
        return token.path("accessToken").asText();
    }

    private JsonNode request(HttpClient client, URI base, String method, String path,
            String token, Object body, int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(20));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        request.header("Content-Type", "application/json").method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(expectedStatus);
        return json.readTree(response.body());
    }

    private static void assertMoney(JsonNode actual, String expected) {
        assertThat(new BigDecimal(actual.asText())).isEqualByComparingTo(expected);
    }

    private static String fixture(String template, String database, String email, String amount) {
        String result = replaceDeclaration(template, "expected_database", sqlLiteral(database));
        result = replaceDeclaration(result, "normalized_email", sqlLiteral(email));
        return replaceDeclaration(result, "funding_amount_text", sqlLiteral(amount));
    }

    private static String replaceDeclaration(String template, String name, String sqlValue) {
        Pattern declaration = Pattern.compile("(?m)^(\\s*" + Pattern.quote(name)
                + "\\s+text\\s*:=\\s*)(?:'(?:''|[^'])*'|NULL)(\\s*;)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = declaration.matcher(template);
        if (matcher.results().count() != 1) {
            throw new IllegalArgumentException("Expected exactly one editable declaration for " + name);
        }
        return declaration.matcher(template).replaceFirst("$1" + Matcher.quoteReplacement(sqlValue) + "$2");
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static void rejectsWithoutWrites(JdbcTemplate jdbc, String sql, String sqlState) {
        DatabaseSnapshot before = snapshot(jdbc);
        assertThatThrownBy(() -> jdbc.execute(sql))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo(sqlState));
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }

    private static void fundsWithoutOtherWrites(JdbcTemplate jdbc, String sql, String email, String amount) {
        DatabaseSnapshot before = snapshot(jdbc);
        Long targetId = jdbc.queryForObject("select w.id from paynest.wallets w join paynest.users u "
                + "on u.id = w.user_id where u.email = ?", Long.class, email);
        List<Map<String, Object>> expectedWallets = before.wallets().stream().map(wallet -> {
            Map<String, Object> expected = new HashMap<>(wallet);
            if (targetId.equals(wallet.get("id"))) {
                expected.put("balance", ((BigDecimal) wallet.get("balance")).add(new BigDecimal(amount)));
            }
            return expected;
        }).toList();
        jdbc.execute(sql);
        assertThat(snapshot(jdbc)).isEqualTo(new DatabaseSnapshot(before.users(), expectedWallets, before.transactions()));
    }

    private record DatabaseSnapshot(List<Map<String, Object>> users,
            List<Map<String, Object>> wallets, List<Map<String, Object>> transactions) {
    }

    private static DatabaseSnapshot snapshot(JdbcTemplate jdbc) {
        return new DatabaseSnapshot(jdbc.queryForList("select * from paynest.users order by id"),
                wallets(jdbc), jdbc.queryForList("select * from paynest.transactions order by id"));
    }

    private static List<Map<String, Object>> wallets(JdbcTemplate jdbc) {
        return jdbc.queryForList("select * from paynest.wallets order by id");
    }

    private static BigDecimal balance(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject("select w.balance from paynest.wallets w "
                + "join paynest.users u on u.id = w.user_id where u.email = ?", BigDecimal.class, email);
    }

    private static int transactionCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from paynest.transactions", Integer.class);
    }
}
