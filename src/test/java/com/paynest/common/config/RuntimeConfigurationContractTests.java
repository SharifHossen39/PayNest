package com.paynest.common.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationContractTests {

    private static final Set<String> EXPECTED_ENVIRONMENT_VARIABLES = Set.of(
            "PAYNEST_DB_URL",
            "PAYNEST_DB_USERNAME",
            "PAYNEST_DB_PASSWORD",
            "PAYNEST_TEST_DB_URL",
            "PAYNEST_TEST_DB_USERNAME",
            "PAYNEST_TEST_DB_PASSWORD",
            "PAYNEST_JWT_SECRET",
            "PAYNEST_JWT_TTL",
            "PAYNEST_DB_LOCK_TIMEOUT");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void sharedConfigurationUsesSafeLocalUsernameDefault() {
        contextRunner.run(context -> assertThat(
                context.getEnvironment().getProperty("spring.datasource.username"))
                .isEqualTo("paynest_local"));
    }

    @Test
    void sharedConfigurationBindsDevelopmentDatabaseOverridesFromEnvironmentNames() {
        contextRunner.withPropertyValues(
                "PAYNEST_DB_URL=jdbc:postgresql://localhost:5432/paynest_override",
                "PAYNEST_DB_USERNAME=override_user",
                "PAYNEST_DB_PASSWORD=override-password")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://localhost:5432/paynest_override");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                            .isEqualTo("override_user");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                            .isEqualTo("override-password");
                });
    }

    @Test
    void testProfileBindsDatabaseJwtTtlAndLockOverridesFromEnvironmentNames() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=test",
                "PAYNEST_TEST_DB_URL=jdbc:postgresql://localhost:5432/paynest_contract_test",
                "PAYNEST_TEST_DB_USERNAME=contract_test_user",
                "PAYNEST_TEST_DB_PASSWORD=contract-test-password",
                "PAYNEST_JWT_SECRET=Y29udHJhY3QtdGVzdC1qd3Qtc2VjcmV0LTMyLWJ5dGVzIQ==",
                "PAYNEST_JWT_TTL=17m",
                "PAYNEST_DB_LOCK_TIMEOUT=900ms")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:postgresql://localhost:5432/paynest_contract_test");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                            .isEqualTo("contract_test_user");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                            .isEqualTo("contract-test-password");
                    assertThat(context.getEnvironment().getProperty("paynest.jwt.secret"))
                            .isEqualTo("Y29udHJhY3QtdGVzdC1qd3Qtc2VjcmV0LTMyLWJ5dGVzIQ==");
                    assertThat(context.getEnvironment().getProperty("paynest.jwt.ttl"))
                            .isEqualTo("17m");
                    assertThat(context.getEnvironment().getProperty(
                            "paynest.database.lock-timeout")).isEqualTo("900ms");
                });
    }

    @Test
    void committedEnvironmentSampleDocumentsExactlyNineSafeValues() throws IOException {
        Properties sample = new Properties();
        Path samplePath = Path.of(System.getProperty("user.dir"), ".env.example");

        try (Reader reader = Files.newBufferedReader(samplePath)) {
            sample.load(reader);
        }

        assertThat(sample.stringPropertyNames()).isEqualTo(EXPECTED_ENVIRONMENT_VARIABLES);
        assertThat(sample.getProperty("PAYNEST_DB_PASSWORD"))
                .isEqualTo("replace-with-local-database-password");
        assertThat(sample.getProperty("PAYNEST_TEST_DB_PASSWORD"))
                .isEqualTo("replace-with-test-database-password");
        assertThat(sample.getProperty("PAYNEST_JWT_SECRET"))
                .isEqualTo("replace-with-base64-encoded-32-byte-secret");
        assertThat(sample.values()).allSatisfy(value -> assertThat(value.toString())
                .doesNotContain("password123", "secret123", "prod"));
    }
}
