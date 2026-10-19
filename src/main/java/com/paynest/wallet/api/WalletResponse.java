package com.paynest.wallet.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.paynest.wallet.domain.Wallet;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WalletResponse", description = "Current wallet balance")
public record WalletResponse(
        @Schema(
                description = "Exact USD balance with two fractional digits",
                example = "1250.00",
                type = "number",
                types = "number",
                format = "decimal",
                multipleOf = 0.01,
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal balance,
        @Schema(
                example = "USD",
                allowableValues = "USD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(
                example = "2026-10-19T12:00:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getBalance(), wallet.getCurrency(), wallet.getCreatedAt());
    }
}
