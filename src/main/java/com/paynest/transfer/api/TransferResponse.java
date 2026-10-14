package com.paynest.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        TransactionDetails transaction,
        BigDecimal balanceAfter
) {

    public record TransactionDetails(
            Long id,
            String direction,
            Counterparty counterparty,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt
    ) {
    }

    public record Counterparty(String name, String email) {
    }
}
