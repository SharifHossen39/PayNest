package com.paynest.wallet.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.paynest.wallet.domain.Wallet;

public record WalletResponse(BigDecimal balance, String currency, Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getBalance(), wallet.getCurrency(), wallet.getCreatedAt());
    }
}
