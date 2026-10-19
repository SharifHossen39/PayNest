package com.paynest.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SignUpRequest", description = "Account registration details")
public record SignUpRequest(
        @Schema(
                description = "Display name after surrounding Unicode whitespace is removed",
                example = "Ada Payne",
                minLength = 2,
                maxLength = 100,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(
                description = "Email address with surrounding Unicode whitespace stripped and then lowercased before validation",
                example = "ada.payne@example.test",
                format = "email",
                maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(
                description = "Password preserved exactly as supplied",
                example = "correct-horse-placeholder",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = 8,
                maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
