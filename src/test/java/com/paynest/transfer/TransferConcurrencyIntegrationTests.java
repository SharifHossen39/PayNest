package com.paynest.transfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.paynest.common.error.InsufficientBalanceException;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.transfer.api.TransferRequest;
import com.paynest.transfer.api.TransferResponse;
import com.paynest.transfer.application.TransferService;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyIntegrationTests {

    private static final Clock FIXTURE_CLOCK = Clock.fixed(
            Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanGuardedDatabase() {
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @Test
    void competingOverspendCommitsOnceWithoutANegativeBalanceOrLostUpdate() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "100.00");
        Wallet firstRecipient = persistFundedWallet("First", "first", "0.00");
        Wallet secondRecipient = persistFundedWallet("Second", "second", "0.00");

        List<Object> outcomes = runTogether(
                () -> transfer(sender.getUser().getId(), firstRecipient.getUser().getEmail(), "80.00"),
                () -> transfer(sender.getUser().getId(), secondRecipient.getUser().getEmail(), "80.00"));

        assertThat(outcomes).filteredOn(TransferResponse.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(InsufficientBalanceException.class::isInstance).hasSize(1);
        assertThat(balance(sender)).isEqualByComparingTo("20.00");
        assertThat(List.of(balance(firstRecipient), balance(secondRecipient)))
                .containsExactlyInAnyOrder(new BigDecimal("80.00"), new BigDecimal("0.00"));
        assertThat(transactionCount()).isOne();
        assertThat(minimumBalance()).isGreaterThanOrEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void oppositeDirectionTransfersBothCommitWithExactBalances() throws Exception {
        Wallet first = persistFundedWallet("First", "first", "100.00");
        Wallet second = persistFundedWallet("Second", "second", "100.00");

        List<Object> outcomes = runTogether(
                () -> transfer(first.getUser().getId(), second.getUser().getEmail(), "10.00"),
                () -> transfer(second.getUser().getId(), first.getUser().getEmail(), "20.00"));

        assertThat(outcomes).allMatch(TransferResponse.class::isInstance);
        assertThat(balance(first)).isEqualByComparingTo("110.00");
        assertThat(balance(second)).isEqualByComparingTo("90.00");
        assertThat(transactionCount()).isEqualTo(2);
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void heldWalletLockTimesOutOnceAndRollsBackWithoutStateChange() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "100.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "25.00");

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockWallet(blocker, sender.getId());
            Instant started = Instant.now();

            Throwable failure = capture(() -> transfer(
                    sender.getUser().getId(), recipient.getUser().getEmail(), "10.00"));

            Duration elapsed = Duration.between(started, Instant.now());
            assertThat(sqlState(failure)).isEqualTo("55P03");
            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(9));
            assertThat(balance(blocker, sender)).isEqualByComparingTo("100.00");
            assertThat(balance(blocker, recipient)).isEqualByComparingTo("25.00");
            assertThat(transactionCount(blocker)).isZero();
            blocker.rollback();
        }

        assertThat(balance(sender)).isEqualByComparingTo("100.00");
        assertThat(balance(recipient)).isEqualByComparingTo("25.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void locksLowerWalletBeforeWaitingForBlockedHigherWallet() throws Exception {
        Wallet lowerIdSender = persistFundedWallet("Sender", "sender", "100.00");
        Wallet higherIdRecipient = persistFundedWallet("Recipient", "recipient", "25.00");
        assertThat(lowerIdSender.getId()).isLessThan(higherIdRecipient.getId());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockWallet(blocker, higherIdRecipient.getId());
            Future<TransferResponse> transfer = executor.submit(() -> transfer(
                    lowerIdSender.getUser().getId(),
                    higherIdRecipient.getUser().getEmail(),
                    "10.00"));

            assertThat(waitUntilWalletIsLocked(lowerIdSender.getId(), Duration.ofSeconds(3)))
                    .isTrue();
            blocker.commit();

            assertThat(transfer.get(5, TimeUnit.SECONDS).balanceAfter())
                    .isEqualByComparingTo("90.00");
        } finally {
            executor.shutdownNow();
        }

        assertThat(balance(lowerIdSender)).isEqualByComparingTo("90.00");
        assertThat(balance(higherIdRecipient)).isEqualByComparingTo("35.00");
        assertThat(transactionCount()).isOne();
    }

    @Test
    void everyPooledSessionUsesTheExternalizedFiveSecondLockTimeout() {
        assertThat(jdbcTemplate.queryForObject(
                "select current_setting('lock_timeout')", String.class)).isEqualTo("5s");
    }

    @Test
    void databaseConstraintRejectsNegativeWalletBalance() {
        Wallet wallet = persistFundedWallet("Owner", "owner", "10.00");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update paynest.wallets set balance = -0.01 where id = ?", wallet.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(wallet)).isEqualByComparingTo("10.00");
    }

    private TransferResponse transfer(long senderId, String recipientEmail, String amount) {
        return transferService.transfer(
                senderId, new TransferRequest(recipientEmail, new BigDecimal(amount)));
    }

    private List<Object> runTogether(Callable<TransferResponse> first, Callable<TransferResponse> second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstFuture = executor.submit(concurrentOutcome(first, ready, start));
            Future<Object> secondFuture = executor.submit(concurrentOutcome(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(firstFuture.get(15, TimeUnit.SECONDS), secondFuture.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Object> concurrentOutcome(
            Callable<TransferResponse> operation,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return operation.call();
            } catch (Throwable failure) {
                return failure;
            }
        };
    }

    private Wallet persistFundedWallet(String name, String emailPrefix, String initialBalance) {
        User user = userRepository.saveAndFlush(User.create(
                name,
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque",
                FIXTURE_CLOCK));
        Wallet wallet = walletRepository.saveAndFlush(Wallet.create(user, FIXTURE_CLOCK));
        jdbcTemplate.update(
                "update paynest.wallets set balance = ? where id = ?",
                new BigDecimal(initialBalance),
                wallet.getId());
        return wallet;
    }

    private BigDecimal balance(Wallet wallet) {
        return jdbcTemplate.queryForObject(
                "select balance from paynest.wallets where id = ?",
                BigDecimal.class,
                wallet.getId());
    }

    private static BigDecimal balance(Connection connection, Wallet wallet) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select balance from paynest.wallets where id = ?")) {
            statement.setLong(1, wallet.getId());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal(1);
            }
        }
    }

    private long transactionCount() {
        return jdbcTemplate.queryForObject("select count(*) from paynest.transactions", Long.class);
    }

    private static long transactionCount(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*) from paynest.transactions");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private BigDecimal minimumBalance() {
        return jdbcTemplate.queryForObject("select min(balance) from paynest.wallets", BigDecimal.class);
    }

    private static void lockWallet(Connection connection, long walletId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from paynest.wallets where id = ? for update")) {
            statement.setLong(1, walletId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private boolean waitUntilWalletIsLocked(long walletId, Duration maximumWait) throws Exception {
        Instant deadline = Instant.now().plus(maximumWait);
        while (Instant.now().isBefore(deadline)) {
            try (Connection probe = dataSource.getConnection();
                 PreparedStatement statement = probe.prepareStatement(
                         "select id from paynest.wallets where id = ? for update nowait")) {
                statement.setLong(1, walletId);
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                }
            } catch (java.sql.SQLException failure) {
                if ("55P03".equals(failure.getSQLState())) {
                    return true;
                }
                throw failure;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        return false;
    }

    private static Throwable capture(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.sql.SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}
