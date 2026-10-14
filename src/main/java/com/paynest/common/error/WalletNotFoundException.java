package com.paynest.common.error;

public final class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException() {
        super("Wallet not found");
    }
}
