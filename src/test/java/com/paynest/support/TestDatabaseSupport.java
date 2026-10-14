package com.paynest.support;

import org.springframework.jdbc.core.JdbcOperations;

public final class TestDatabaseSupport {

    private static final String REQUIRED_DATABASE = "paynest_test";

    private TestDatabaseSupport() {
    }

    public static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " must be set for the dedicated local paynest_test PostgreSQL database");
        }
        return value;
    }

    public static void verifyDedicatedTestDatabase(JdbcOperations jdbc) {
        String currentDatabase = jdbc.queryForObject("select current_database()", String.class);
        if (!REQUIRED_DATABASE.equals(currentDatabase)) {
            throw new IllegalStateException(
                    "Refusing destructive test setup: current database must be exactly '"
                            + REQUIRED_DATABASE + "' but was '" + currentDatabase
                            + "'. Configure PAYNEST_TEST_DB_URL, PAYNEST_TEST_DB_USERNAME, "
                            + "and PAYNEST_TEST_DB_PASSWORD for the dedicated test database.");
        }
    }

    public static void cleanApplicationData(JdbcOperations jdbc) {
        verifyDedicatedTestDatabase(jdbc);
        jdbc.update("delete from paynest.wallets");
        jdbc.update("delete from paynest.users");
    }

    public static void resetPayNestSchema(JdbcOperations jdbc) {
        verifyDedicatedTestDatabase(jdbc);
        jdbc.execute("drop schema if exists paynest cascade");
    }
}
