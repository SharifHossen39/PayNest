package com.paynest.common.error;

import java.util.Map;

public final class InvalidSignUpException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidSignUpException(Map<String, String> fieldErrors) {
        super("Signup validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
