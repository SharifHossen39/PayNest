package com.paynest.common.error;

public final class SelfTransferNotAllowedException extends RuntimeException {

    public SelfTransferNotAllowedException() {
        super("Transfers to the same user are not allowed");
    }
}
