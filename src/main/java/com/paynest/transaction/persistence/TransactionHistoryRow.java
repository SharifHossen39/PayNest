package com.paynest.transaction.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.paynest.transaction.domain.TransactionStatus;

public record TransactionHistoryRow(
        Long id,
        Long senderWalletId,
        String senderName,
        String senderEmail,
        Long receiverWalletId,
        String receiverName,
        String receiverEmail,
        BigDecimal amount,
        TransactionStatus status,
        Instant createdAt
) {
}
