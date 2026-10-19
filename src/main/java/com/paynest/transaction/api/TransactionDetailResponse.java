package com.paynest.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDetailResponse(
        Long id,
        String direction,
        Participant sender,
        Participant recipient,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt
) {
    public record Participant(String name, String email) {
    }
}
