package com.paynest.auth.api;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    public TokenResponse(String accessToken, Instant expiresAt) {
        this(accessToken, "Bearer", expiresAt);
    }
}
