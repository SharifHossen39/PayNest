package com.paynest.common.error;

import java.util.Map;

public final class InvalidSignInException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidSignInException(Map<String, String> fieldErrors) {
        super("Signin validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
