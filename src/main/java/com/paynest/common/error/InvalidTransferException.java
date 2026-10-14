package com.paynest.common.error;

import java.util.Map;

public final class InvalidTransferException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidTransferException(Map<String, String> fieldErrors) {
        super("Transfer validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
