package com.paynest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(OutputCaptureExtension.class)
class PayNestApplicationLoggingTests {

    @Test
    void startupSuccessIsLoggedOnlyAfterSpringReturns(CapturedOutput output) {
        String[] arguments = {"--spring.main.banner-mode=off"};
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(PayNestApplication.class, arguments))
                    .thenReturn(mock(ConfigurableApplicationContext.class));

            PayNestApplication.main(arguments);
        }

        assertThat(output.getAll()).contains("event=application outcome=started");
    }

    @Test
    void failedStartupDoesNotEmitStartedOutcome(CapturedOutput output) {
        String[] arguments = {"--spring.main.banner-mode=off"};
        IllegalStateException startupFailure = new IllegalStateException("startup failed");
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(PayNestApplication.class, arguments))
                    .thenThrow(startupFailure);

            assertThatThrownBy(() -> PayNestApplication.main(arguments)).isSameAs(startupFailure);
        }

        assertThat(output.getAll()).doesNotContain("event=application outcome=started");
    }
}
