package com.paynest.common.error;

public final class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email already exists");
    }
}
