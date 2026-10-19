package com.paynest.transaction.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TransactionHistoryPageResponse", description = "Page of transaction summaries")
public record TransactionHistoryPageResponse(
        @ArraySchema(
                schema = @Schema(implementation = TransactionHistoryItemResponse.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<TransactionHistoryItemResponse> content,
        @Schema(example = "0", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(example = "20", minimum = "1", maximum = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(example = "42", minimum = "0", format = "int64", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(example = "3", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,
        @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean last
) {
}
