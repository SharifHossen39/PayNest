package com.paynest.auth.application;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import com.paynest.auth.api.SignUpRequest;
import com.paynest.common.error.DuplicateEmailException;
import com.paynest.common.error.InvalidSignUpException;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-07T08:30:00.123456789Z"), ZoneOffset.UTC);
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(
                userRepository, walletRepository, passwordEncoder, CLOCK, VALIDATOR);
    }

    @Test
    void canonicalizesBeforeLookupAndPreservesEveryPasswordCodePoint() {
        String password = "\uD83D\uDE00".repeat(64) + "Passw0rd";
        when(passwordEncoder.encode(password)).thenReturn("{pbkdf2@SpringSecurity_v5_8}encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.register(new SignUpRequest(
                "\u2003Ada Lovelace\u00A0",
                "\u2003ADA@EXAMPLE.COM\u00A0",
                password));

        verify(userRepository).existsByEmail("ada@example.com");
        verify(passwordEncoder).encode(password);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("Ada Lovelace");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsInvalidCanonicalFieldsBeforeLookupEncodingOrPersistence() {
        SignUpRequest invalid = new SignUpRequest(
                " \uD83D\uDE00 ",
                " not-an-email ",
                "\uD83D\uDE00".repeat(73));

        assertThatThrownBy(() -> registrationService.register(invalid))
                .isInstanceOf(InvalidSignUpException.class)
                .satisfies(failure -> assertThat(((InvalidSignUpException) failure).getFieldErrors())
                        .containsOnlyKeys("name", "email", "password"));

        verifyNoInteractions(userRepository, walletRepository, passwordEncoder);
    }

    @Test
    void passwordBelowEightCodePointsNeverReachesTheEncoderOrRepositories() {
        assertThatThrownBy(() -> registrationService.register(
                new SignUpRequest("Valid Name", "valid@example.com", "short")))
                .isInstanceOf(InvalidSignUpException.class);

        verify(passwordEncoder, never()).encode(any());
        verifyNoInteractions(userRepository, walletRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "first..last@example.com",
            "person@example..com",
            "person@-example.com",
            "person@example-.com",
            "per\u0001son@example.com"
    })
    void rejectsMalformedCanonicalEmailBeforeEncodingOrPersistence(String email) {
        assertValidationError(new SignUpRequest("Valid Name", email, "password"), "email");
        verifyNoInteractions(userRepository, walletRepository, passwordEncoder);
    }

    @Test
    void rejectsDatabaseUnrepresentableNameAndIllFormedUnicodePasswordBeforeDependencies() {
        assertValidationError(
                new SignUpRequest("Invalid\0Name", "valid@example.com", "password"),
                "name");
        assertValidationError(
                new SignUpRequest("Valid Name", "valid@example.com", "password\uD800"),
                "password");

        verifyNoInteractions(userRepository, walletRepository, passwordEncoder);
    }

    @Test
    void acceptsExactNameEmailAndPasswordBoundaries() {
        allowPersistence();
        String maxEmail = emailAtLimit();

        registrationService.register(new SignUpRequest("AB", maxEmail, "12345678"));
        registrationService.register(new SignUpRequest(
                "N".repeat(100), "upper-bound@example.com", "\uD83D\uDE00".repeat(72)));

        verify(userRepository).existsByEmail(maxEmail);
        verify(userRepository).existsByEmail("upper-bound@example.com");
        verify(userRepository, times(2)).saveAndFlush(any(User.class));
        verify(walletRepository, times(2)).saveAndFlush(any(Wallet.class));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("outsideBoundaryRequests")
    void rejectsValuesImmediatelyOutsideExactBoundaries(
            @SuppressWarnings("unused") String description,
            SignUpRequest request,
            String field
    ) {
        assertValidationError(request, field);
        verifyNoInteractions(userRepository, walletRepository, passwordEncoder);
    }

    @Test
    void translatesOnlyTheWrappedNamedUserEmailConstraintToDuplicateEmail() {
        when(passwordEncoder.encode("valid-password"))
                .thenReturn("{pbkdf2@SpringSecurity_v5_8}encoded");
        ConstraintViolationException namedConstraint = new ConstraintViolationException(
                "insert failed",
                new SQLException("duplicate", "23505"),
                "uk_users_email");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "write failed", namedConstraint));

        assertThatThrownBy(() -> registrationService.register(new SignUpRequest(
                "Valid Name", "duplicate@example.com", "valid-password")))
                .isInstanceOf(DuplicateEmailException.class);

        verifyNoInteractions(walletRepository);
    }

    private void allowPersistence() {
        when(passwordEncoder.encode(any())).thenReturn("{pbkdf2@SpringSecurity_v5_8}encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertValidationError(SignUpRequest request, String field) {
        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(InvalidSignUpException.class)
                .satisfies(failure -> assertThat(((InvalidSignUpException) failure).getFieldErrors())
                        .containsOnlyKeys(field));
    }

    private static Stream<Arguments> outsideBoundaryRequests() {
        return Stream.of(
                arguments("one-code-point name",
                        new SignUpRequest("A", "valid@example.com", "password"), "name"),
                arguments("101-code-point name",
                        new SignUpRequest("N".repeat(101), "valid@example.com", "password"), "name"),
                arguments("255-code-point email",
                        new SignUpRequest("Valid Name", emailAtLimit() + "x", "password"), "email"),
                arguments("seven-code-point password",
                        new SignUpRequest("Valid Name", "valid@example.com", "1234567"), "password"),
                arguments("73-code-point password",
                        new SignUpRequest("Valid Name", "valid@example.com", "P".repeat(73)), "password"));
    }

    private static String emailAtLimit() {
        String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        String email = "a".repeat(64) + "@" + domain;
        assertThat(email).hasSize(254);
        return email;
    }
}
