package com.paynest.transaction.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.paynest.transaction.domain.TransactionStatus;

public record TransactionDetailRow(
        Long id,
        Long senderWalletId,
        String senderName,
        String senderEmail,
        String recipientName,
        String recipientEmail,
        BigDecimal amount,
        TransactionStatus status,
        Instant createdAt
) {
}
