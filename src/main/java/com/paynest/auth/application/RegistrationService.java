package com.paynest.auth.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paynest.auth.api.SignUpRequest;
import com.paynest.auth.api.SignUpResponse;
import com.paynest.common.error.DuplicateEmailException;
import com.paynest.common.error.InvalidSignUpException;
import com.paynest.user.api.UserResponse;
import com.paynest.user.domain.EmailNormalizer;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.api.WalletResponse;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;

@Service
public class RegistrationService {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    private static final String USER_EMAIL_CONSTRAINT = "uk_users_email";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Validator validator;

    public RegistrationService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Validator validator
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.validator = validator;
    }

    @Transactional
    public SignUpResponse register(SignUpRequest request) {
        CanonicalSignUp canonical = validate(request);
        if (userRepository.existsByEmail(canonical.email())) {
            throw new DuplicateEmailException();
        }

        User user = User.create(
                canonical.name(),
                canonical.email(),
                passwordEncoder.encode(canonical.password()),
                clock);
        try {
            userRepository.saveAndFlush(user);
            Wallet wallet = walletRepository.saveAndFlush(Wallet.create(user, clock));
            return new SignUpResponse(UserResponse.from(user), WalletResponse.from(wallet));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, USER_EMAIL_CONSTRAINT)) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    private CanonicalSignUp validate(SignUpRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            throw new InvalidSignUpException(Map.of("request", "Request body is required"));
        }

        String normalizedName = normalizeName(request.name(), errors);
        String normalizedEmail = normalizeEmail(request.email(), errors);
        validatePassword(request.password(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidSignUpException(errors);
        }
        return new CanonicalSignUp(normalizedName, normalizedEmail, request.password());
    }

    private String normalizeName(String name, Map<String, String> errors) {
        if (name == null) {
            errors.put("name", "Name is required");
            return "";
        }
        String normalized = EmailNormalizer.stripUnicodeEdges(name);
        int length = codePointLength(normalized);
        if (normalized.isBlank()
                || length < MIN_NAME_LENGTH
                || length > MAX_NAME_LENGTH
                || normalized.indexOf('\0') >= 0
                || !isWellFormedUnicode(normalized)) {
            errors.put("name", "Name must contain between 2 and 100 characters");
        }
        return normalized;
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
        if (codePointLength(normalized) > MAX_EMAIL_LENGTH
                || !validator.validate(new CanonicalEmail(normalized)).isEmpty()) {
            errors.put("email", "Email must be a valid address of at most 254 characters");
        }
        return normalized;
    }

    private void validatePassword(String password, Map<String, String> errors) {
        if (password == null) {
            errors.put("password", "Password is required");
            return;
        }
        int length = codePointLength(password);
        if (length < MIN_PASSWORD_LENGTH
                || length > MAX_PASSWORD_LENGTH
                || !isWellFormedUnicode(password)) {
            errors.put("password", "Password must contain between 8 and 72 characters");
        }
    }

    private static boolean isWellFormedUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean hasConstraint(Throwable failure, String constraintName) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintName.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record CanonicalSignUp(String name, String email, String password) {
    }

    private record CanonicalEmail(@Email String value) {
    }
}
