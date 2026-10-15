package com.paynest.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryItemResponse(
        Long id,
        String direction,
        Counterparty counterparty,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {

    public record Counterparty(String name, String email) {
    }
}
