package com.paynest.common.config;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(OutputCaptureExtension.class)
class DatabaseLockPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(LockConfiguration.class);

    @Test
    void usesFiveSecondDefault() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(DatabaseLockProperties.class).lockTimeout())
                    .isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void productionEnvironmentPropertyBindsNonDefaultDuration() {
        contextRunner.withPropertyValues("PAYNEST_DB_LOCK_TIMEOUT=750ms").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBean(DatabaseLockProperties.class).lockTimeout())
                    .isEqualTo(Duration.ofMillis(750));
        });
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidDurations")
    void rejectsUnsafeDurations(
            @SuppressWarnings("unused") String description,
            String configuredDuration
    ) {
        contextRunner.withPropertyValues(
                "paynest.database.lock-timeout=" + configuredDuration).run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "Database lock timeout must be between 1ms and 2147483647ms");
                });
    }

    private static Stream<Arguments> invalidDurations() {
        return Stream.of(
                arguments("zero", "0ms"),
                arguments("negative", "-1ms"),
                arguments("positive sub-millisecond", "500us"),
                arguments("above PostgreSQL maximum", "2147483648ms"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DatabaseLockProperties.class)
    static class LockConfiguration {
    }
}
