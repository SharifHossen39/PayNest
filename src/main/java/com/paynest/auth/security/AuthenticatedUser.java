package com.paynest.auth.security;

public record AuthenticatedUser(long id) {

    public AuthenticatedUser {
        if (id <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }
}
