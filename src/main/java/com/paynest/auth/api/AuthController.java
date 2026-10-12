package com.paynest.auth.api;

import com.paynest.auth.application.AuthenticationService;
import com.paynest.auth.application.RegistrationService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

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
    public SignUpResponse signUp(@RequestBody SignUpRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/signin")
    public TokenResponse signIn(@RequestBody SignInRequest request) {
        return authenticationService.authenticate(request);
    }
}
