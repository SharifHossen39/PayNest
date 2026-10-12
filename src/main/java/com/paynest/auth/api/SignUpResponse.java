package com.paynest.auth.api;

import com.paynest.user.api.UserResponse;
import com.paynest.wallet.api.WalletResponse;

public record SignUpResponse(UserResponse user, WalletResponse wallet) {
}
