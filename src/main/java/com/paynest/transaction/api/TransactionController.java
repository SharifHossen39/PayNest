package com.paynest.transaction.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.common.config.OpenApiConfig;
import com.paynest.common.error.ApiError;
import com.paynest.common.error.InvalidPaginationException;
import com.paynest.common.error.InvalidTransactionIdException;
import com.paynest.transaction.application.TransactionQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Transaction history and detail")
public class TransactionController {

    private final TransactionQueryService transactionQueryService;

    public TransactionController(TransactionQueryService transactionQueryService) {
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    @Operation(
            operationId = "getTransactionHistory",
            summary = "Get transaction history",
            description = "Returns a page ordered by newest transaction first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history page",
                    content = @Content(mediaType = "application/json", schema = @Schema(
                            implementation = TransactionHistoryPageResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Pagination validation failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer token is missing or invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "invalidToken",
                                    summary = "The bearer token is missing or invalid",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Authentication token is invalid\",\"path\":\"/api/transactions\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Current wallet was not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public TransactionHistoryPageResponse history(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(
                    description = "Zero-based page index",
                    schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(required = false) String page,
            @Parameter(
                    description = "Number of transactions per page",
                    schema = @Schema(
                            type = "integer",
                            format = "int32",
                            defaultValue = "20",
                            minimum = "1",
                            maximum = "100"))
            @RequestParam(required = false) String size
    ) {
        return transactionQueryService.history(
                user.id(),
                paginationValue(page, 0, "page"),
                paginationValue(size, 20, "size"));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getTransactionDetail",
            summary = "Get a transaction by ID",
            description = "Returns a transaction only when the current wallet participated in it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction detail",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionDetailResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Transaction ID validation failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer token is missing or invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "invalidToken",
                                    summary = "The bearer token is missing or invalid",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Authentication token is invalid\",\"path\":\"/api/transactions/42\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transaction or current wallet was not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public TransactionDetailResponse detail(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(
                    description = "Positive transaction identifier",
                    required = true,
                    schema = @Schema(type = "integer", format = "int64", minimum = "1"))
            @PathVariable String id
    ) {
        return transactionQueryService.detail(user.id(), transactionId(id));
    }

    private static long transactionId(String value) {
        if (value == null || !value.matches("[0-9]+")) {
            throw new InvalidTransactionIdException("Value must be a positive decimal BIGINT");
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new InvalidTransactionIdException("Value must be a positive decimal BIGINT");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new InvalidTransactionIdException("Value must be a positive decimal BIGINT");
        }
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
