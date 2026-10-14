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
    void migratesV1ThenV2ThenV3WithoutChangingPriorChecksumsOrCatalogContracts() {
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

        Flyway v2Flyway = flyway("classpath:db/migration-fixtures/v2", "2");
        v2Flyway.migrate();

        assertThat(v2Flyway.validateWithResult().validationSuccessful).isTrue();
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

        List<Integer> priorChecksums = appliedChecksums(jdbc);
        Flyway productionV2Flyway = flyway("classpath:db/migration", "2");
        assertThat(productionV2Flyway.validateWithResult().validationSuccessful).isTrue();

        Flyway v3Flyway = flyway("classpath:db/migration", "3");
        v3Flyway.migrate();

        assertThat(v3Flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(appliedChecksums(jdbc).subList(0, 2)).containsExactlyElementsOf(priorChecksums);
        assertThat(jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' and table_name <> 'flyway_schema_history' order by table_name",
                String.class)).containsExactly("transactions", "users", "wallets");
        assertThat(jdbc.queryForObject(
                "select identity_generation from information_schema.columns "
                        + "where table_schema = 'paynest' and table_name = 'transactions' and column_name = 'id'",
                String.class)).isEqualTo("BY DEFAULT");
        assertThat(jdbc.queryForList(
                "select column_name || ':' || data_type || ':' || is_nullable || ':' "
                        + "|| coalesce(character_maximum_length::text, '') || ':' "
                        + "|| coalesce(numeric_precision::text, '') || ':' "
                        + "|| coalesce(numeric_scale::text, '') "
                        + "from information_schema.columns "
                        + "where table_schema = 'paynest' and table_name = 'transactions' "
                        + "order by ordinal_position",
                String.class)).containsExactly(
                        "id:bigint:NO::64:0",
                        "sender_wallet_id:bigint:NO::64:0",
                        "receiver_wallet_id:bigint:NO::64:0",
                        "amount:numeric:NO::19:2",
                        "status:character varying:NO:20::",
                        "created_at:timestamp with time zone:NO:::");
        assertThat(constraintDefinition(jdbc, "fk_transactions_sender_wallet"))
                .contains("FOREIGN KEY (sender_wallet_id)")
                .contains("REFERENCES paynest.wallets(id)");
        assertThat(constraintDefinition(jdbc, "fk_transactions_receiver_wallet"))
                .contains("FOREIGN KEY (receiver_wallet_id)")
                .contains("REFERENCES paynest.wallets(id)");
        assertThat(constraintDefinition(jdbc, "ck_transactions_positive_amount"))
                .contains("amount > 0.00").contains("NaN");
        assertThat(constraintDefinition(jdbc, "ck_transactions_distinct_wallets"))
                .contains("sender_wallet_id <> receiver_wallet_id");
        assertThat(constraintDefinition(jdbc, "ck_transactions_completed_status"))
                .contains("status").contains("COMPLETED");
        assertThat(constraintNames(jdbc)).containsExactly(
                "ck_transactions_completed_status",
                "ck_transactions_distinct_wallets",
                "ck_transactions_positive_amount",
                "fk_transactions_receiver_wallet",
                "fk_transactions_sender_wallet",
                "nn_transactions_amount",
                "nn_transactions_created_at",
                "nn_transactions_receiver_wallet",
                "nn_transactions_sender_wallet",
                "nn_transactions_status",
                "transactions_id_not_null",
                "transactions_pkey");
        assertThat(foreignKeyDeleteActions(jdbc)).containsExactly(
                "fk_transactions_receiver_wallet:a",
                "fk_transactions_sender_wallet:a");
        assertThat(indexDefinition(jdbc, "idx_transactions_sender_history")).isEqualTo(
                "CREATE INDEX idx_transactions_sender_history ON paynest.transactions USING btree "
                        + "(sender_wallet_id, created_at DESC, id DESC)");
        assertThat(indexDefinition(jdbc, "idx_transactions_receiver_history")).isEqualTo(
                "CREATE INDEX idx_transactions_receiver_history ON paynest.transactions USING btree "
                        + "(receiver_wallet_id, created_at DESC, id DESC)");
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

    private List<Integer> appliedChecksums(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select checksum from paynest.flyway_schema_history "
                        + "where version is not null order by installed_rank",
                Integer.class);
    }

    private String indexDefinition(JdbcTemplate jdbc, String indexName) {
        return jdbc.queryForObject(
                "select pg_get_indexdef(indexrelid) from pg_index "
                        + "where indexrelid = ('paynest.' || ?)::regclass",
                String.class,
                indexName);
    }

    private List<String> constraintNames(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select conname from pg_constraint "
                        + "where conrelid = 'paynest.transactions'::regclass order by conname",
                String.class);
    }

    private List<String> foreignKeyDeleteActions(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "select conname || ':' || confdeltype::text from pg_constraint "
                        + "where conrelid = 'paynest.transactions'::regclass "
                        + "and contype = 'f' order by conname",
                String.class);
    }
}
