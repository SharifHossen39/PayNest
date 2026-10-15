package com.paynest.transaction.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.common.error.InvalidPaginationException;
import com.paynest.transaction.application.TransactionQueryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    public TransactionController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    public TransactionHistoryPageResponse history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size
    ) {
        return transactionQueryService.history(
                user.id(),
                paginationValue(page, 0, "page"),
                paginationValue(size, 20, "size"));
    }

    private static int paginationValue(String value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException(field, "Value has an invalid format");
        }
    }
}
