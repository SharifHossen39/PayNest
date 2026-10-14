package com.paynest.common.error;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

    @Test
    void nestedPostgresDeadlockReturnsCompleteSafeConflictContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                Clock.fixed(NOW, ZoneOffset.UTC));
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
    }
}
