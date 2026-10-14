package com.paynest.transfer.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.paynest.common.error.InsufficientBalanceException;
import com.paynest.common.error.InvalidTransferException;
import com.paynest.common.error.SelfTransferNotAllowedException;
import com.paynest.common.error.UserNotFoundException;
import com.paynest.common.error.WalletNotFoundException;
import com.paynest.transaction.domain.Transaction;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.transfer.api.TransferRequest;
import com.paynest.transfer.api.TransferResponse;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-14T10:15:30.123456789Z"), ZoneOffset.UTC);
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                userRepository, walletRepository, transactionRepository, CLOCK, VALIDATOR);
    }

    @Test
    void locksBothWalletsOnceAndMapsRolesByUserIdRatherThanLockResultPosition() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(22L, sender, "500.00");
        Wallet recipientWallet = wallet(11L, recipient, "100.00");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 41L);
            return transaction;
        });

        TransferResponse response = transferService.transfer(
                sender.getId(),
                new TransferRequest("\u2003RECIPIENT@EXAMPLE.COM\u00a0", new BigDecimal("150")));

        assertThat(senderWallet.getBalance()).isEqualByComparingTo("350.00");
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo("250.00");
        assertThat(response).isEqualTo(new TransferResponse(
                new TransferResponse.TransactionDetails(
                        41L,
                        "SENT",
                        new TransferResponse.Counterparty("Recipient", "recipient@example.com"),
                        new BigDecimal("150.00"),
                        "USD",
                        "COMPLETED",
                        Instant.parse("2026-10-14T10:15:30.123456Z")),
                new BigDecimal("350.00")));
        verify(userRepository).findByEmail("recipient@example.com");
        verify(walletRepository).findAllByUserIdsForUpdate(List.of(1L, 2L));
        verifyNoMoreInteractions(walletRepository);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void transfersTheMaximumAmountWhenItExactlyMatchesTheSenderBalance() {
        BigDecimal maximum = new BigDecimal("99999999999999999.99");
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, maximum.toPlainString());
        Wallet recipientWallet = wallet(22L, recipient, "0.00");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 42L);
            return transaction;
        });

        TransferResponse response = transferService.transfer(
                sender.getId(), new TransferRequest(recipient.getEmail(), maximum));

        assertBalances(senderWallet, "0.00", recipientWallet, maximum.toPlainString());
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction persisted = transactionCaptor.getValue();
        assertThat(persisted.getSenderWallet()).isSameAs(senderWallet);
        assertThat(persisted.getReceiverWallet()).isSameAs(recipientWallet);
        assertThat(persisted.getAmount()).isEqualByComparingTo(maximum);
        assertThat(response).isEqualTo(new TransferResponse(
                new TransferResponse.TransactionDetails(
                        42L,
                        "SENT",
                        new TransferResponse.Counterparty("Recipient", "recipient@example.com"),
                        maximum,
                        "USD",
                        "COMPLETED",
                        Instant.parse("2026-10-14T10:15:30.123456Z")),
                new BigDecimal("0.00")));
    }

    @Test
    void normalizesAndLooksUpAValidRecipientEmailAtThe254CharacterBoundary() {
        User sender = user(1L, "Sender", "sender@example.com");
        String normalizedEmail = emailAtLimit();
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(normalizedEmail)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(
                        "\u2003" + normalizedEmail.toUpperCase() + "\u00a0",
                        new BigDecimal("1.00"))))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findByEmail(normalizedEmail);
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @ParameterizedTest(name = "rejects invalid request: {0}")
    @MethodSource("invalidRequests")
    void rejectsEveryInvalidRequestBeforeAnyRepositoryCall(
            @SuppressWarnings("unused") String description,
            TransferRequest request,
            String expectedField
    ) {
        assertThatThrownBy(() -> transferService.transfer(1L, request))
                .isInstanceOf(InvalidTransferException.class)
                .satisfies(failure -> assertThat(((InvalidTransferException) failure).getFieldErrors())
                        .containsKey(expectedField));

        verifyNoInteractions(userRepository, walletRepository, transactionRepository);
    }

    @Test
    void reportsAllInvalidRequestFieldsTogether() {
        assertThatThrownBy(() -> transferService.transfer(
                1L, new TransferRequest("not-an-email", BigDecimal.ZERO)))
                .isInstanceOf(InvalidTransferException.class)
                .satisfies(failure -> assertThat(((InvalidTransferException) failure).getFieldErrors())
                        .containsOnlyKeys("recipientEmail", "amount"));

        verifyNoInteractions(userRepository, walletRepository, transactionRepository);
    }

    @Test
    void rejectsUnknownSenderWithoutLookingUpRecipientOrMutatingAnything() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(
                99L, new TransferRequest("recipient@example.com", new BigDecimal("1.00"))))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).findByEmail(any());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void rejectsUnknownRecipientWithoutLoadingWallets() {
        User sender = user(1L, "Sender", "sender@example.com");
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest("missing@example.com", new BigDecimal("1.00"))))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void rejectsSelfTransferBeforeLoadingWallets() {
        User sender = user(1L, "Sender", "sender@example.com");
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(sender.getEmail())).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(sender.getEmail(), new BigDecimal("1.00"))))
                .isInstanceOf(SelfTransferNotAllowedException.class);

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void rejectsMissingSenderWalletBeforeLoadingRecipientWallet() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(recipient.getEmail())).thenReturn(Optional.of(recipient));
        when(walletRepository.findAllByUserIdsForUpdate(List.of(sender.getId(), recipient.getId())))
                .thenReturn(List.of());

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("1.00"))))
                .isInstanceOf(WalletNotFoundException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void rejectsMissingRecipientWalletWithoutMutatingSenderWallet() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "500.00");
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(recipient.getEmail())).thenReturn(Optional.of(recipient));
        when(walletRepository.findAllByUserIdsForUpdate(List.of(sender.getId(), recipient.getId())))
                .thenReturn(List.of(senderWallet));

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("150.00"))))
                .isInstanceOf(WalletNotFoundException.class);

        assertThat(senderWallet.getBalance()).isEqualByComparingTo("500.00");
        verifyNoInteractions(transactionRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEitherNonUsdWalletBeforeMutatingEitherWallet(boolean senderIsNonUsd) {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "500.00");
        Wallet recipientWallet = wallet(22L, recipient, "100.00");
        ReflectionTestUtils.setField(senderIsNonUsd ? senderWallet : recipientWallet, "currency", "EUR");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("150.00"))))
                .isInstanceOf(InvalidTransferException.class)
                .satisfies(failure -> assertThat(((InvalidTransferException) failure).getFieldErrors())
                        .containsOnlyKeys("currency"));

        assertBalances(senderWallet, "500.00", recipientWallet, "100.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void rejectsInsufficientBalanceBeforeMutatingEitherWallet() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "100.00");
        Wallet recipientWallet = wallet(22L, recipient, "50.00");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("100.01"))))
                .isInstanceOf(InsufficientBalanceException.class);

        assertBalances(senderWallet, "100.00", recipientWallet, "50.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void recipientOverflowOccursAfterSenderDebit() {
        BigDecimal maximum = new BigDecimal("99999999999999999.99");
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "25.00");
        Wallet recipientWallet = wallet(22L, recipient, maximum.toPlainString());
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Balance exceeds the supported maximum");

        assertBalances(senderWallet, "24.00", recipientWallet, maximum.toPlainString());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void clockFailureOccursAfterBothWalletMutationsAndBeforeRepositorySave() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "80.00");
        Wallet recipientWallet = wallet(22L, recipient, "20.00");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);
        RuntimeException clockFailure = new IllegalStateException("forced clock failure");
        Clock throwingClock = mock(Clock.class);
        when(throwingClock.instant()).thenThrow(clockFailure);
        transferService = new TransferService(
                userRepository,
                walletRepository,
                transactionRepository,
                throwingClock,
                VALIDATOR);

        Throwable failure = catchThrowable(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("15.00"))));

        assertThat(failure).isSameAs(clockFailure);
        assertBalances(senderWallet, "65.00", recipientWallet, "35.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void repositorySaveFailureOccursAfterMutationsAndClockStampedTransactionCreation() {
        User sender = user(1L, "Sender", "sender@example.com");
        User recipient = user(2L, "Recipient", "recipient@example.com");
        Wallet senderWallet = wallet(11L, sender, "80.00");
        Wallet recipientWallet = wallet(22L, recipient, "20.00");
        allowPartyResolution(sender, recipient, senderWallet, recipientWallet);
        RuntimeException saveFailure = new IllegalStateException("forced transaction save failure");
        when(transactionRepository.save(any(Transaction.class))).thenThrow(saveFailure);

        Throwable failure = catchThrowable(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("15.00"))));

        assertThat(failure).isSameAs(saveFailure);
        assertBalances(senderWallet, "65.00", recipientWallet, "35.00");
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getCreatedAt())
                .isEqualTo(Instant.parse("2026-10-14T10:15:30.123456Z"));
    }

    private void allowPartyResolution(
            User sender,
            User recipient,
            Wallet senderWallet,
            Wallet recipientWallet
    ) {
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail(recipient.getEmail())).thenReturn(Optional.of(recipient));
        when(walletRepository.findAllByUserIdsForUpdate(List.of(sender.getId(), recipient.getId())))
                .thenReturn(senderWallet.getId() < recipientWallet.getId()
                        ? List.of(senderWallet, recipientWallet)
                        : List.of(recipientWallet, senderWallet));
    }

    private static User user(Long id, String name, String email) {
        User user = User.create(name, email, "{hash}opaque", CLOCK);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Wallet wallet(Long id, User user, String balance) {
        Wallet wallet = Wallet.create(user, CLOCK);
        BigDecimal initialBalance = new BigDecimal(balance);
        if (initialBalance.signum() > 0) {
            wallet.credit(initialBalance);
        }
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    private static void assertBalances(
            Wallet senderWallet,
            String senderBalance,
            Wallet recipientWallet,
            String recipientBalance
    ) {
        assertThat(senderWallet.getBalance()).isEqualByComparingTo(senderBalance);
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo(recipientBalance);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(
                arguments("missing request", null, "request"),
                arguments("missing email", new TransferRequest(null, BigDecimal.ONE), "recipientEmail"),
                arguments("blank email", new TransferRequest(" \u2003 ", BigDecimal.ONE), "recipientEmail"),
                arguments("malformed email", new TransferRequest("not-an-email", BigDecimal.ONE), "recipientEmail"),
                arguments("overlong email", new TransferRequest(
                        "a".repeat(243) + "@example.com", BigDecimal.ONE), "recipientEmail"),
                arguments("ill-formed email", new TransferRequest(
                        "person\uD800@example.com", BigDecimal.ONE), "recipientEmail"),
                arguments("missing amount", new TransferRequest("valid@example.com", null), "amount"),
                arguments("zero amount", new TransferRequest(
                        "valid@example.com", BigDecimal.ZERO), "amount"),
                arguments("negative amount", new TransferRequest(
                        "valid@example.com", new BigDecimal("-0.01")), "amount"),
                arguments("over-scale amount", new TransferRequest(
                        "valid@example.com", new BigDecimal("1.001")), "amount"),
                arguments("overflowing amount", new TransferRequest(
                        "valid@example.com", new BigDecimal("100000000000000000.00")), "amount"));
    }

    private static String emailAtLimit() {
        String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        String email = "a".repeat(64) + "@" + domain;
        assertThat(email).hasSize(254);
        return email;
    }
}
