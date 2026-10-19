package com.paynest.user.api;

import com.paynest.auth.security.AuthenticatedUser;
import com.paynest.common.config.OpenApiConfig;
import com.paynest.common.error.ApiError;
import com.paynest.user.application.UserProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User", description = "Authenticated user profile")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(
            operationId = "getCurrentUser",
            summary = "Get the current user profile",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current user profile",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Bearer token is missing or invalid",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "invalidToken",
                                    summary = "The bearer token is missing or invalid",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Authentication token is invalid\",\"path\":\"/api/users/me\"}")))
    })
    public UserResponse currentProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return userProfileService.currentProfile(user.id());
    }
}
