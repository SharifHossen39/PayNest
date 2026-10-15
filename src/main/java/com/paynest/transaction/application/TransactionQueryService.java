package com.paynest.transaction.application;

import java.util.List;

import com.paynest.common.error.InvalidPaginationException;
import com.paynest.common.error.WalletNotFoundException;
import com.paynest.transaction.api.TransactionHistoryItemResponse;
import com.paynest.transaction.api.TransactionHistoryItemResponse.Counterparty;
import com.paynest.transaction.api.TransactionHistoryPageResponse;
import com.paynest.transaction.persistence.TransactionHistoryRow;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionQueryService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionHistoryPageResponse history(long userId, int page, int size) {
        validatePagination(page, size);
        Long walletId = walletRepository.findByUserId(userId)
                .map(Wallet::getId)
                .orElseThrow(WalletNotFoundException::new);
        Page<TransactionHistoryRow> result = transactionRepository.findHistoryByParticipant(
                walletId,
                PageRequest.of(page, size));
        List<TransactionHistoryItemResponse> content = result.getContent().stream()
                .map(row -> map(row, walletId))
                .toList();
        return new TransactionHistoryPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast());
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("page", "Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException("size", "Size must be between 1 and 100");
        }
    }

    private static TransactionHistoryItemResponse map(TransactionHistoryRow row, Long walletId) {
        boolean sent = walletId.equals(row.senderWalletId());
        Counterparty counterparty = sent
                ? new Counterparty(row.receiverName(), row.receiverEmail())
                : new Counterparty(row.senderName(), row.senderEmail());
        return new TransactionHistoryItemResponse(
                row.id(),
                sent ? "SENT" : "RECEIVED",
                counterparty,
                row.amount(),
                Wallet.USD,
                row.status().name(),
                row.createdAt());
    }
}
