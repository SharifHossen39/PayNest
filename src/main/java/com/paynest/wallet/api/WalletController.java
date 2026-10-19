package com.paynest.wallet.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.common.config.OpenApiConfig;
import com.paynest.common.error.ApiError;
import com.paynest.wallet.application.WalletQueryService;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "Wallet balance and transfers")
public class WalletController {

    private final WalletQueryService walletQueryService;

    public WalletController(WalletQueryService walletQueryService) {
        this.walletQueryService = walletQueryService;
    }

    @GetMapping
    @Operation(
            operationId = "getCurrentWallet",
            summary = "Get the current wallet",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current wallet",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WalletResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer token is missing or invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "invalidToken",
                                    summary = "The bearer token is missing or invalid",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Authentication token is invalid\",\"path\":\"/api/wallet\"}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Wallet could not be loaded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public WalletResponse currentWallet(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return walletQueryService.currentWallet(user.id());
    }
}
