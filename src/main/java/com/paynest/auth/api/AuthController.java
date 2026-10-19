package com.paynest.auth.api;

import com.paynest.auth.application.AuthenticationService;
import com.paynest.auth.application.RegistrationService;
import com.paynest.common.error.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Account registration and token issuance")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService
    ) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "signUp",
            summary = "Create a user account and wallet",
            description = "Registers an account and creates its empty USD wallet."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Account and wallet created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SignUpResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email is already registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "duplicateEmail",
                                    summary = "The normalized email is already registered",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":409,\"error\":\"EMAIL_ALREADY_EXISTS\",\"message\":\"An account with this email already exists\",\"path\":\"/api/auth/signup\"}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Account creation failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public SignUpResponse signUp(@RequestBody SignUpRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/signin")
    @Operation(
            operationId = "signIn",
            summary = "Sign in and issue a bearer token",
            description = "Authenticates an account with its email and password."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Bearer token issued",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request validation failed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(
                                    name = "invalidCredentials",
                                    summary = "The supplied credentials could not be authenticated",
                                    value = "{\"timestamp\":\"2026-10-19T12:10:00Z\",\"status\":401,\"error\":\"AUTHENTICATION_FAILED\",\"message\":\"Authentication failed\",\"path\":\"/api/auth/signin\"}")))
    })
    public TokenResponse signIn(@RequestBody SignInRequest request) {
        TokenResponse response = authenticationService.authenticate(request);
        LOGGER.info("event=authentication outcome=success");
        return response;
    }
}
