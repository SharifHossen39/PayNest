package com.paynest.common.error;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Test
    void nestedPostgresDeadlockReturnsCompleteSafeConflictContract(CapturedOutput output) {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/wallet/transfers");
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "private outer detail",
                new SQLException("private database detail", "40P01"));

        ResponseEntity<ApiError> response = handler.handleTransferPersistenceFailure(
                failure, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                NOW,
                409,
                "CONFLICT",
                "The transfer conflicted with another request",
                "/api/wallet/transfers",
                java.util.Map.of()));
        assertThat(response.toString())
                .doesNotContain("private outer detail", "private database detail", "40P01");
        assertThat(output.getAll())
                .contains("event=transfer outcome=conflict category=concurrency")
                .doesNotContain("private outer detail", "private database detail", "40P01");
    }

    @Test
    void unsupportedMediaTypeUsesApprovedValidationContract() {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/signin");

        ResponseEntity<ApiError> response = handler.handleUnsupportedMediaType(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                NOW,
                400,
                "VALIDATION_ERROR",
                "Request validation failed",
                "/api/auth/signin",
                Map.of()));
    }

    @Test
    void authenticationFailureLogsOnlyItsFixedSafeCategory(CapturedOutput output) {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/signin");

        handler.handleAuthenticationFailed(request);

        assertThat(output.getAll())
                .contains("event=authentication outcome=failure category=invalid_credentials")
                .doesNotContain("/api/auth/signin");
    }

    @Test
    void unexpectedTransferFailureUsesTransactionFailedContractAndSafeLog(CapturedOutput output) {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/wallet/transfers");

        ResponseEntity<ApiError> response = handler.handleUnexpectedFailure(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new ApiError(
                NOW,
                500,
                "TRANSACTION_FAILED",
                "The transfer could not be completed",
                "/api/wallet/transfers",
                Map.of()));
        assertThat(output.getAll())
                .contains("event=transfer outcome=failure category=unexpected")
                .doesNotContain("/api/wallet/transfers");
    }

    @Test
    void unexpectedNonTransferFailureUsesInternalErrorAndSafeDiagnosticCategory(
            CapturedOutput output
    ) {
        GlobalExceptionHandler handler = handler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/wallet");

        ResponseEntity<ApiError> response = handler.handleUnexpectedFailure(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        assertThat(output.getAll())
                .contains("event=request outcome=failure category=unexpected")
                .doesNotContain("/api/wallet");
    }

    private static GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(new ApiErrorResponseWriter(
                Clock.fixed(NOW, ZoneOffset.UTC), new ObjectMapper()));
    }
}
