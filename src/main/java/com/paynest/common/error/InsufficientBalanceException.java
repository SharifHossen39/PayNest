package com.paynest.common.error;

public final class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException() {
        super("Insufficient wallet balance");
    }
}
