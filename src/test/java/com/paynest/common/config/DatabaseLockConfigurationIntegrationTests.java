package com.paynest.common.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "PAYNEST_DB_LOCK_TIMEOUT=750ms",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.maximum-pool-size=2"
})
@ActiveProfiles("test")
class DatabaseLockConfigurationIntegrationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void nonDefaultTimeoutInitializesMultipleSimultaneouslyBorrowedConnections() throws Exception {
        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            assertThat(List.of(lockTimeout(first), lockTimeout(second)))
                    .containsExactly("750ms", "750ms");
            assertThat(backendPid(first)).isNotEqualTo(backendPid(second));
        }
    }

    private static String lockTimeout(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select current_setting('lock_timeout')")) {
            result.next();
            return result.getString(1);
        }
    }

    private static int backendPid(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select pg_backend_pid()")) {
            result.next();
            return result.getInt(1);
        }
    }
}
