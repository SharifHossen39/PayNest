package com.paynest.auth.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResponse", description = "Issued bearer token")
public record TokenResponse(
        @Schema(
                description = "Opaque JWT bearer credential",
                example = "placeholder.access.token",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
        @Schema(
                description = "Authorization scheme",
                example = "Bearer",
                allowableValues = "Bearer",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,
        @Schema(
                description = "Token expiration instant",
                example = "2026-10-19T13:00:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt
) {

    public TokenResponse(String accessToken, Instant expiresAt) {
        this(accessToken, "Bearer", expiresAt);
    }
}
