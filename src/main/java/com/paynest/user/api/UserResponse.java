package com.paynest.user.api;

import java.time.Instant;

import com.paynest.user.domain.User;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse", description = "Public user profile")
public record UserResponse(
        @Schema(
                example = "42",
                format = "int64",
                minimum = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(example = "Ada Payne", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(
                example = "ada.payne@example.test",
                format = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(
                example = "2026-10-19T12:00:00Z",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
