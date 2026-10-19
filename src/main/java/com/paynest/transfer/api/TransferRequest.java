package com.paynest.transfer.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransferRequest", description = "USD transfer details")
public record TransferRequest(
        @Schema(
                description = "Registered recipient email address with surrounding Unicode whitespace stripped and then lowercased before validation and lookup",
                example = "grace.hopper@example.test",
                format = "email",
                maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String recipientEmail,
        @Schema(
                description = "Positive exact USD amount; values with more than two fractional digits, including trailing zeros such as 25.500, are rejected",
                example = "25.50",
                type = "number",
                types = "number",
                format = "decimal",
                minimum = "0.01",
                maximum = "99999999999999999.99",
                multipleOf = 0.01,
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount
) {
}
