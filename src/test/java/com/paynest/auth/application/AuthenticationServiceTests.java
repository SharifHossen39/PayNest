package com.paynest.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.paynest.auth.api.SignInRequest;
import com.paynest.auth.api.TokenResponse;
import com.paynest.auth.security.JwtProperties;
import com.paynest.auth.security.JwtService;
import com.paynest.common.error.AuthenticationFailedException;
import com.paynest.common.error.InvalidSignInException;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-07T08:30:00.987654321Z"), ZoneOffset.UTC);
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String VALID_SECRET =
            "dGVzdC1vbmx5LXBheW5lc3Qtand0LXNlY3JldC0zMmI=";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private User user;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(
                new JwtProperties(VALID_SECRET, Duration.ofMinutes(60)),
                CLOCK);
        authenticationService = new AuthenticationService(
                userRepository, passwordEncoder, jwtService, VALIDATOR);
    }

    @Test
    void canonicalizesEmailAndPassesThePasswordUnchanged() {
        String password = "  " + "\uD83D\uDE00".repeat(70);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("{encoded}hash");
        when(user.getId()).thenReturn(42L);
        when(passwordEncoder.matches(password, "{encoded}hash")).thenReturn(true);

        TokenResponse response = authenticationService.authenticate(
                new SignInRequest("\u2003ADA@EXAMPLE.COM\u00A0", password));

        verify(userRepository).findByEmail("ada@example.com");
        verify(passwordEncoder).matches(password, "{encoded}hash");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-10-07T09:30:00Z"));
    }

    @Test
    void malformedInputFailsBeforeLookupOrPasswordVerification() {
        assertThatThrownBy(() -> authenticationService.authenticate(
                new SignInRequest("not-an-email", "")))
                .isInstanceOf(InvalidSignInException.class)
                .satisfies(failure -> assertThat(((InvalidSignInException) failure).getFieldErrors())
                        .containsOnlyKeys("email", "password"));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void unknownEmailAndWrongPasswordUseTheSameGenericFailure() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("known@example.com")).thenReturn(Optional.of(user));
        when(user.getPasswordHash()).thenReturn("{encoded}hash");
        when(passwordEncoder.matches("wrong", "{encoded}hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.authenticate(
                new SignInRequest("unknown@example.com", "anything")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");
        assertThatThrownBy(() -> authenticationService.authenticate(
                new SignInRequest("known@example.com", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Authentication failed");

        verify(passwordEncoder, never()).matches("anything", "{encoded}hash");
        verify(passwordEncoder).matches("wrong", "{encoded}hash");
    }
}
