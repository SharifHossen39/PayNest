package com.paynest.transaction.api;

import java.util.List;

public record TransactionHistoryPageResponse(
        List<TransactionHistoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
