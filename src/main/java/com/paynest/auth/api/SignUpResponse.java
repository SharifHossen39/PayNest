package com.paynest.auth.api;

import com.paynest.user.api.UserResponse;
import com.paynest.wallet.api.WalletResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SignUpResponse", description = "New user and wallet")
public record SignUpResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserResponse user,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        WalletResponse wallet
) {
}
