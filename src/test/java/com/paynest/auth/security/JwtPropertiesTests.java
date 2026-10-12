package com.paynest.auth.security;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(OutputCaptureExtension.class)
class JwtPropertiesTests {

    private static final String VALID_SECRET =
            "dGVzdC1vbmx5LXBheW5lc3Qtand0LXNlY3JldC0zMmI=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(JwtConfiguration.class);

    @Test
    void bindsValidSecretAndUsesTheSixtyMinuteDefaultTtl() {
        contextRunner
                .withPropertyValues("paynest.jwt.secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(JwtProperties.class).ttl())
                            .isEqualTo(Duration.ofMinutes(60));
                });
    }

    @Test
    void acceptsAConfiguredPositiveTtlOverride() {
        contextRunner
                .withPropertyValues(
                        "paynest.jwt.secret=" + VALID_SECRET,
                        "paynest.jwt.ttl=15m")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(JwtProperties.class).ttl())
                            .isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void productionEnvironmentPlaceholdersBindSecretAndTtl() {
        contextRunner
                .withPropertyValues(
                        "PAYNEST_JWT_SECRET=" + VALID_SECRET,
                        "PAYNEST_JWT_TTL=37m")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(JwtProperties.class).ttl())
                            .isEqualTo(Duration.ofMinutes(37));
                });
    }

    @Test
    void acceptsTheExactOneSecondMinimum() {
        contextRunner
                .withPropertyValues(
                        "paynest.jwt.secret=" + VALID_SECRET,
                        "paynest.jwt.ttl=1s")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(JwtProperties.class).ttl())
                            .isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    void malformedProductionSecretIsAbsentFromFailureAndCapturedOutput(CapturedOutput output) {
        String sensitiveSecret = "sensitive-production-secret-not-base64!";

        contextRunner
                .withPropertyValues(
                        "PAYNEST_JWT_SECRET=" + sensitiveSecret,
                        "PAYNEST_JWT_TTL=60m")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull().hasStackTraceContaining(
                            "JWT secret must be valid Base64");
                    assertThat(stackTrace(failure)).doesNotContain(sensitiveSecret);
                });

        assertThat(output.getAll()).doesNotContain(sensitiveSecret);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidConfiguration")
    void invalidJwtConfigurationFailsTheApplicationContext(
            @SuppressWarnings("unused") String description,
            String[] properties,
            String expectedFailure
    ) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(expectedFailure);
        });
    }

    private static Stream<Arguments> invalidConfiguration() {
        return Stream.of(
                arguments("missing secret", new String[]{"paynest.jwt.secret="},
                        "JWT secret is required"),
                arguments("malformed secret", new String[]{"paynest.jwt.secret=not-base64!"},
                        "JWT secret must be valid Base64"),
                arguments("short secret", new String[]{"paynest.jwt.secret=c2hvcnQ="},
                        "JWT secret must decode to at least 32 bytes"),
                arguments("zero ttl", new String[]{
                        "paynest.jwt.secret=" + VALID_SECRET, "paynest.jwt.ttl=0s"},
                        "JWT TTL must be a positive whole-second duration"),
                arguments("negative ttl", new String[]{
                        "paynest.jwt.secret=" + VALID_SECRET, "paynest.jwt.ttl=-1s"},
                        "JWT TTL must be a positive whole-second duration"),
                arguments("positive sub-second ttl", new String[]{
                        "paynest.jwt.secret=" + VALID_SECRET, "paynest.jwt.ttl=999ms"},
                        "JWT TTL must be a positive whole-second duration"),
                arguments("fractional-second ttl", new String[]{
                        "paynest.jwt.secret=" + VALID_SECRET, "paynest.jwt.ttl=1500ms"},
                        "JWT TTL must be a positive whole-second duration"));
    }

    private static String stackTrace(Throwable failure) {
        StringWriter output = new StringWriter();
        failure.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfiguration {
    }
}
