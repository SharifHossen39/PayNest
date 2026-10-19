package com.paynest.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SignInRequest", description = "Account credentials")
public record SignInRequest(
        @Schema(
                description = "Registered email address with surrounding Unicode whitespace stripped and then lowercased before validation and lookup",
                example = "ada.payne@example.test",
                format = "email",
                maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(
                description = "Exact account password",
                example = "correct-horse-placeholder",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                minLength = 1,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
