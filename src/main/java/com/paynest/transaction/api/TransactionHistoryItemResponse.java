package com.paynest.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransactionHistoryItemResponse", description = "Viewer-relative transaction summary")
public record TransactionHistoryItemResponse(
        @Schema(
                example = "701",
                format = "int64",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(
                example = "SENT",
                allowableValues = {"SENT", "RECEIVED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Counterparty counterparty,
        @Schema(
                example = "25.50",
                type = "number",
                types = "number",
                format = "decimal",
                multipleOf = 0.01,
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,
        @Schema(
                example = "USD",
                allowableValues = "USD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(
                example = "COMPLETED",
                allowableValues = "COMPLETED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(
                example = "2026-10-19T12:05:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {

    @Schema(name = "TransactionCounterparty", description = "Other transaction participant")
    public record Counterparty(
            @Schema(example = "Grace Hopper", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(
                    example = "grace.hopper@example.test",
                    format = "email",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String email
    ) {
    }
}
