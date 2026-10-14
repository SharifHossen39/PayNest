package com.paynest.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
        Long transactionId,
        String direction,
        String recipientName,
        String recipientEmail,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        BigDecimal balanceAfter
) {
}
