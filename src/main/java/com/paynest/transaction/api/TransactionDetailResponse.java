package com.paynest.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransactionDetailResponse", description = "Transaction detail for a participant")
public record TransactionDetailResponse(
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
        Participant sender,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Participant recipient,
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
    @Schema(name = "TransactionParticipant", description = "Transaction sender or recipient")
    public record Participant(
            @Schema(example = "Ada Payne", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(
                    example = "ada.payne@example.test",
                    format = "email",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String email
    ) {
    }
}
