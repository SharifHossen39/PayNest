package com.paynest.transfer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.transfer.api.TransferRequest;
import com.paynest.transfer.application.TransferService;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@ActiveProfiles("test")
@Import(TransferRollbackIntegrationTests.RollbackClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TransferRollbackIntegrationTests {

    private static final Instant NORMAL_TEST_TIME = Instant.parse("2026-10-14T09:00:00Z");
    private static final Clock FIXTURE_CLOCK = Clock.fixed(NORMAL_TEST_TIME, ZoneOffset.UTC);
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private static final BigDecimal TRIGGER_AMOUNT = new BigDecimal("18473.29");
    private static final String TRIGGER_MESSAGE = "test transaction rejection 2-3";
    private static final String TRIGGER_SQL_STATE = "23514";

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ControllableClock testClock;

    @BeforeEach
    void setUp() {
        testClock.reset();
        removeTransactionFailureTrigger();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        testClock.reset();
        removeTransactionFailureTrigger();
    }

    @Test
    void recipientOverflowAfterSenderDebitRollsBackBothBalancesAndPersistsNoTransaction(
            CapturedOutput output
    ) {
        Wallet sender = persistFundedWallet("Overflow Sender", "overflow-sender", "25.00");
        Wallet recipient = persistFundedWallet(
                "Overflow Recipient", "overflow-recipient", MAX_BALANCE.toPlainString());
        BigDecimal senderBalanceBefore = balance(sender.getId());
        BigDecimal recipientBalanceBefore = balance(recipient.getId());
        int operationLogStart = output.getAll().length();

        Throwable failure = catchThrowable(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipient.getUser().getEmail(), new BigDecimal("1.00"))));
        String operationLogs = output.getAll().substring(operationLogStart);

        assertThat(operationLogs).isBlank();
        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Balance exceeds the supported maximum");
        assertRolledBack(sender, senderBalanceBefore, recipient, recipientBalanceBefore);
    }

    @Test
    void forcedClockFailureAfterBothMutationsEscapesAsSameInstanceAndRollsBackEverything(
            CapturedOutput output
    ) {
        Wallet sender = persistFundedWallet("Clock Sender", "clock-sender", "80.00");
        Wallet recipient = persistFundedWallet("Clock Recipient", "clock-recipient", "20.00");
        BigDecimal senderBalanceBefore = balance(sender.getId());
        BigDecimal recipientBalanceBefore = balance(recipient.getId());
        ForcedTransferFailure forcedFailure = new ForcedTransferFailure();
        testClock.failWith(forcedFailure);
        int operationLogStart = output.getAll().length();

        Throwable failure = catchThrowable(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipient.getUser().getEmail(), new BigDecimal("15.00"))));
        String operationLogs = output.getAll().substring(operationLogStart);

        assertThat(operationLogs).isBlank();
        assertThat(failure).isSameAs(forcedFailure);
        assertRolledBack(sender, senderBalanceBefore, recipient, recipientBalanceBefore);
    }

    @Test
    void rejectedTransactionInsertPropagatesTranslatedFailureRollsBackAndLogsNoInternals(
            CapturedOutput output
    ) {
        installTransactionFailureTrigger();
        Wallet sender = persistFundedWallet("Trigger Sender", "trigger-sender", "20000.00");
        Wallet recipient = persistFundedWallet("Trigger Recipient", "trigger-recipient", "10.00");
        BigDecimal senderBalanceBefore = balance(sender.getId());
        BigDecimal recipientBalanceBefore = balance(recipient.getId());
        int operationLogStart = output.getAll().length();

        Throwable failure = catchThrowable(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipient.getUser().getEmail(), TRIGGER_AMOUNT)));
        String operationLogs = output.getAll().substring(operationLogStart);

        assertThat(operationLogs).isBlank();
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        SQLException sqlFailure = findSqlCause(failure);
        assertThat((Throwable) sqlFailure)
                .isNotNull()
                .hasMessageContaining(TRIGGER_MESSAGE);
        assertThat(sqlFailure.getSQLState()).isEqualTo(TRIGGER_SQL_STATE);
        assertRolledBack(sender, senderBalanceBefore, recipient, recipientBalanceBefore);
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

    private BigDecimal balance(Long walletId) {
        return jdbcTemplate.queryForObject(
                "select balance from paynest.wallets where id = ?",
                BigDecimal.class,
                walletId);
    }

    private void assertRolledBack(
            Wallet sender,
            BigDecimal senderBalanceBefore,
            Wallet recipient,
            BigDecimal recipientBalanceBefore
    ) {
        assertThat(AopUtils.isAopProxy(transferService)).isTrue();
        assertThat(balance(sender.getId())).isEqualByComparingTo(senderBalanceBefore);
        assertThat(balance(recipient.getId())).isEqualByComparingTo(recipientBalanceBefore);
        assertThat(transactionRepository.count()).isZero();
    }

    private void installTransactionFailureTrigger() {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("""
                create function paynest.test_reject_transaction_2_3() returns trigger
                language plpgsql as $$
                begin
                    if new.amount = %s then
                        raise exception '%s' using errcode = '%s';
                    end if;
                    return new;
                end
                $$
                """.formatted(
                TRIGGER_AMOUNT.toPlainString(),
                TRIGGER_MESSAGE,
                TRIGGER_SQL_STATE));
        jdbcTemplate.execute("""
                create trigger test_reject_transaction_2_3 before insert on paynest.transactions
                for each row execute function paynest.test_reject_transaction_2_3()
                """);
    }

    private void removeTransactionFailureTrigger() {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbcTemplate);
        jdbcTemplate.execute(
                "drop trigger if exists test_reject_transaction_2_3 on paynest.transactions");
        jdbcTemplate.execute("drop function if exists paynest.test_reject_transaction_2_3()");
    }

    private static SQLException findSqlCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlFailure) {
                return sqlFailure;
            }
            current = current.getCause();
        }
        return null;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackClockConfiguration {

        @Bean
        @Primary
        ControllableClock rollbackTestClock() {
            return new ControllableClock();
        }
    }

    static final class ControllableClock extends Clock {

        private volatile RuntimeException failure;

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        void reset() {
            failure = null;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) {
                return this;
            }
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            RuntimeException currentFailure = failure;
            if (currentFailure != null) {
                throw currentFailure;
            }
            return NORMAL_TEST_TIME;
        }
    }

    static final class ForcedTransferFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ForcedTransferFailure() {
            super("forced transfer failure 2-3");
        }
    }
}
