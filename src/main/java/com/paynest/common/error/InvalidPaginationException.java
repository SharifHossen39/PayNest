package com.paynest.common.error;

import java.util.Map;

public final class InvalidPaginationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidPaginationException(String field, String message) {
        super("Invalid pagination");
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
