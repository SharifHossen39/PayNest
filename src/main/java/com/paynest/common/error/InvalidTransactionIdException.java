package com.paynest.common.error;

import java.util.Map;

public final class InvalidTransactionIdException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public InvalidTransactionIdException(String message) {
        super("Invalid transaction ID");
        this.fieldErrors = Map.of("id", message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
