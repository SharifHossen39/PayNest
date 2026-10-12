package com.paynest.user.api;

import java.time.Instant;

import com.paynest.user.domain.User;

public record UserResponse(Long id, String name, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
