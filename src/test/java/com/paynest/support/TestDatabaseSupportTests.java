package com.paynest.support;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcOperations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDatabaseSupportTests {

    @Test
    void missingLocalPostgreSqlConfigurationFailsWithTheRequiredVariableName() {
        assertThatThrownBy(() -> TestDatabaseSupport.requiredEnvironmentVariable(
                "PAYNEST_TEST_DB_SETTING_THAT_IS_INTENTIONALLY_UNSET"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYNEST_TEST_DB_SETTING_THAT_IS_INTENTIONALLY_UNSET")
                .hasMessageContaining("paynest_test");
    }

    @Test
    void unsafeDatabaseAbortsBeforeDeletingRowsOrResettingTheSchema() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject("select current_database()", String.class)).thenReturn("paynest");

        assertThatThrownBy(() -> TestDatabaseSupport.cleanApplicationData(jdbc))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paynest_test")
                .hasMessageContaining("PAYNEST_TEST_DB_URL");
        assertThatThrownBy(() -> TestDatabaseSupport.resetPayNestSchema(jdbc))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("paynest_test");

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString());
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
