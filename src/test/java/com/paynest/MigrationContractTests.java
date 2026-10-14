package com.paynest;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paynest.support.TestDatabaseSupport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationContractTests {

    private final String jdbcUrl =
            TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_URL");
    private final String username =
            TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_USERNAME");
    private final String password =
            TestDatabaseSupport.requiredEnvironmentVariable("PAYNEST_TEST_DB_PASSWORD");

    @BeforeEach
    void resetOwnedSchemaAfterSafetyCheck() {
        TestDatabaseSupport.resetPayNestSchema(jdbcTemplate());
    }

    @Test
    void migratesToV1ThenUpgradesWithoutChangingItsChecksumOrCatalogContract() {
        Flyway v1Flyway = flyway("classpath:db/migration-fixtures/v1", "1");
        v1Flyway.migrate();
        JdbcTemplate jdbc = jdbcTemplate();

        List<String> v1Tables = jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' and table_name <> 'flyway_schema_history' order by table_name",
                String.class);
        String userUniqueDefinition = constraintDefinition(jdbc, "uk_users_email");
        Long userId = jdbc.queryForObject(
                "insert into paynest.users(name, email, password_hash, created_at) "
                        + "values (?, ?, ?, ?) returning id",
                Long.class,
                "V1 User",
                "v1@example.com",
                "{hash}opaque",
                Timestamp.from(Instant.parse("2026-10-07T08:30:00Z")));

        assertThat(v1Tables).containsExactly("users");
        assertThat(jdbc.queryForObject("select to_regclass('paynest.wallets')", String.class)).isNull();
        assertThat(userId).isPositive();
        assertThat(userUniqueDefinition).isEqualTo("UNIQUE (email)");

        Flyway productionV1Flyway = flyway("classpath:db/migration", "1");
        assertThat(productionV1Flyway.validateWithResult().validationSuccessful).isTrue();

        Flyway finalFlyway = flyway("classpath:db/migration", "2");
        finalFlyway.migrate();

        assertThat(finalFlyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' and table_name <> 'flyway_schema_history' order by table_name",
                String.class)).containsExactly("users", "wallets");
        assertThat(constraintDefinition(jdbc, "fk_wallets_user"))
                .contains("FOREIGN KEY (user_id)").contains("REFERENCES paynest.users(id)");
        assertThat(constraintDefinition(jdbc, "uk_wallets_user")).isEqualTo("UNIQUE (user_id)");
        assertThat(constraintDefinition(jdbc, "ck_wallets_usd_currency"))
                .contains("currency").contains("USD");
        assertThat(constraintDefinition(jdbc, "ck_wallets_non_negative_balance"))
                .contains("balance").contains(">= 0.00").contains("NaN");
        assertThat(jdbc.queryForList(
                "select table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema = 'paynest' and table_name in ('users', 'wallets') "
                        + "and is_nullable <> 'NO'",
                String.class)).isEmpty();
        assertThat(jdbc.queryForObject(
                "select numeric_precision from information_schema.columns "
                        + "where table_schema = 'paynest' and table_name = 'wallets' and column_name = 'balance'",
                Integer.class)).isEqualTo(19);
        assertThat(jdbc.queryForObject(
                "select numeric_scale from information_schema.columns "
                        + "where table_schema = 'paynest' and table_name = 'wallets' and column_name = 'balance'",
                Integer.class)).isEqualTo(2);

        Long walletId = jdbc.queryForObject(
                "insert into paynest.wallets(user_id, created_at) values (?, ?) returning id",
                Long.class,
                userId,
                Timestamp.from(Instant.parse("2026-10-07T08:30:00Z")));
        BigDecimal defaultBalance = jdbc.queryForObject(
                "select balance from paynest.wallets where id = ?", BigDecimal.class, walletId);
        String defaultCurrency = jdbc.queryForObject(
                "select currency from paynest.wallets where id = ?", String.class, walletId);

        assertThat(defaultBalance).isEqualByComparingTo("0.00");
        assertThat(defaultBalance.scale()).isEqualTo(2);
        assertThat(defaultCurrency).isEqualTo("USD");
        assertThat(jdbc.queryForObject("select count(*) from paynest.users where id = ?", Integer.class, userId))
                .isOne();
    }

    private Flyway flyway(String location, String target) {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .schemas("paynest")
                .defaultSchema("paynest")
                .locations(location)
                .target(target)
                .validateOnMigrate(true)
                .load();
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl, username, password);
        return new JdbcTemplate(dataSource);
    }

    private String constraintDefinition(JdbcTemplate jdbc, String constraintName) {
        return jdbc.queryForObject(
                "select pg_get_constraintdef(oid) from pg_constraint "
                        + "where connamespace = 'paynest'::regnamespace and conname = ?",
                String.class,
                constraintName);
    }
}
