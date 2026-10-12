package com.paynest.auth.application;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paynest.auth.api.SignInRequest;
import com.paynest.auth.api.TokenResponse;
import com.paynest.auth.security.JwtService;
import com.paynest.common.error.AuthenticationFailedException;
import com.paynest.common.error.InvalidSignInException;
import com.paynest.user.domain.EmailNormalizer;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Validator validator;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Validator validator
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public TokenResponse authenticate(SignInRequest request) {
        CanonicalSignIn canonical = validate(request);
        User user = userRepository.findByEmail(canonical.email())
                .orElseThrow(AuthenticationFailedException::new);
        if (!passwordEncoder.matches(canonical.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException();
        }

        JwtService.IssuedToken issuedToken = jwtService.issue(user.getId());
        return new TokenResponse(issuedToken.value(), issuedToken.expiresAt());
    }

    private CanonicalSignIn validate(SignInRequest request) {
        if (request == null) {
            throw new InvalidSignInException(Map.of("request", "Request body is required"));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String email = normalizeEmail(request.email(), errors);
        if (request.password() == null || request.password().isEmpty()) {
            errors.put("password", "Password is required");
        }
        if (!errors.isEmpty()) {
            throw new InvalidSignInException(errors);
        }
        return new CanonicalSignIn(email, request.password());
    }

    private String normalizeEmail(String email, Map<String, String> errors) {
        if (email == null) {
            errors.put("email", "Email is required");
            return "";
        }
        String normalized;
        try {
            normalized = EmailNormalizer.normalize(email);
        } catch (IllegalArgumentException exception) {
            errors.put("email", "Email must be a valid address of at most 254 characters");
            return "";
        }
        if (!validator.validate(new CanonicalEmail(normalized)).isEmpty()) {
            errors.put("email", "Email must be a valid address of at most 254 characters");
        }
        return normalized;
    }

    private record CanonicalSignIn(String email, String password) {
    }

    private record CanonicalEmail(@Email String value) {
    }
}
