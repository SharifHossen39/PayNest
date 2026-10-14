package com.paynest.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseLockConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DatabaseLockConfiguration.class, HikariTestConfiguration.class);

    @Test
    void appliesValidatedDurationAsCanonicalMillisecondsBeforePoolInitialization() {
        contextRunner.withPropertyValues("paynest.database.lock-timeout=750ms").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(HikariDataSource.class).getConnectionInitSql())
                    .isEqualTo("SET lock_timeout TO '750ms'");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class HikariTestConfiguration {

        @Bean(destroyMethod = "close")
        HikariDataSource testDataSource() {
            return new HikariDataSource();
        }
    }
}
