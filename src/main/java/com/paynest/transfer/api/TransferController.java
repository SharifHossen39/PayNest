package com.paynest.transfer.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.common.config.OpenApiConfig;
import com.paynest.common.error.ApiError;
import com.paynest.transfer.application.TransferService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet/transfers")
@Tag(name = "Wallet", description = "Wallet balance and transfers")
public class TransferController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createTransfer",
            summary = "Transfer money from the current wallet",
            description = "Transfers an exact decimal USD amount to another registered user.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Transfer completed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
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
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Authentication token is invalid\",\"path\":\"/api/wallet/transfers\"}"))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Recipient user or wallet was not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Transfer conflicted with another request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "concurrencyConflict",
                                    summary = "Another request holds a conflicting wallet lock",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":409,\"error\":\"CONFLICT\",\"message\":\"The transfer conflicted with another request\",\"path\":\"/api/wallet/transfers\"}"))),
            @ApiResponse(
                    responseCode = "422",
                    description = "Transfer violates a business rule",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "businessRejection",
                                    summary = "A transfer to the current user is not allowed",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":422,\"error\":\"SELF_TRANSFER_NOT_ALLOWED\",\"message\":\"Transfer is not allowed\",\"path\":\"/api/wallet/transfers\"}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Transfer failed and was rolled back",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public TransferResponse transfer(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody TransferRequest request
    ) {
        TransferResponse response = transferService.transfer(user.id(), request);
        LOGGER.info("event=transfer outcome=success");
        return response;
    }
}
