package com.paynest.common.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Shared safe error response")
public record ApiError(
        @Schema(
                example = "2026-10-19T12:10:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant timestamp,
        @Schema(example = "400", minimum = "400", maximum = "599", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(
                description = "Stable machine-readable error code",
                example = "VALIDATION_ERROR",
                allowableValues = {
                        "VALIDATION_ERROR",
                        "AUTHENTICATION_FAILED",
                        "INVALID_TOKEN",
                        "ACCESS_DENIED",
                        "USER_NOT_FOUND",
                        "WALLET_NOT_FOUND",
                        "TRANSACTION_NOT_FOUND",
                        "EMAIL_ALREADY_EXISTS",
                        "CONFLICT",
                        "SELF_TRANSFER_NOT_ALLOWED",
                        "INSUFFICIENT_BALANCE",
                        "TRANSACTION_FAILED",
                        "INTERNAL_ERROR"
                },
                requiredMode = Schema.RequiredMode.REQUIRED)
        String error,
        @Schema(
                description = "Safe client-facing explanation",
                example = "Request validation failed",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(
                description = "Request path",
                example = "/api/auth/signup",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Schema(
                description = "Optional field-to-message validation errors",
                additionalPropertiesSchema = String.class,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Map<String, String> fieldErrors
) {
}
