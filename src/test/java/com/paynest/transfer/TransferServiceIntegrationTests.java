package com.paynest.transfer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.paynest.common.error.InsufficientBalanceException;
import com.paynest.common.error.InvalidTransferException;
import com.paynest.common.error.SelfTransferNotAllowedException;
import com.paynest.common.error.UserNotFoundException;
import com.paynest.common.error.WalletNotFoundException;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.transfer.api.TransferRequest;
import com.paynest.transfer.api.TransferResponse;
import com.paynest.transfer.application.TransferService;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TransferServiceIntegrationTests {

    private static final Clock FIXTURE_CLOCK = Clock.fixed(
            Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC);

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

    @BeforeEach
    void cleanGuardedDatabase() {
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @Test
    void commitsExactBalancesOneCompletedRecordAndSafeResultThroughTheSpringProxy() {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "100.00");

        TransferResponse response = transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(
                        "\u2003" + recipient.getUser().getEmail().toUpperCase() + "\u00a0",
                        new BigDecimal("150")));

        assertThat(balance(sender.getId())).isEqualByComparingTo("350.00");
        assertThat(balance(recipient.getId())).isEqualByComparingTo("250.00");
        assertThat(transactionRepository.count()).isOne();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id, sender_wallet_id, receiver_wallet_id, amount, status, created_at "
                        + "from paynest.transactions");
        assertThat(row.get("sender_wallet_id")).isEqualTo(sender.getId());
        assertThat(row.get("receiver_wallet_id")).isEqualTo(recipient.getId());
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("150.00");
        assertThat(row.get("status")).isEqualTo("COMPLETED");

        assertThat(response).isEqualTo(new TransferResponse(
                ((Number) row.get("id")).longValue(),
                "SENT",
                recipient.getUser().getName(),
                recipient.getUser().getEmail(),
                new BigDecimal("150.00"),
                "USD",
                "COMPLETED",
                ((Timestamp) row.get("created_at")).toInstant(),
                new BigDecimal("350.00")));
    }

    @Test
    void invalidRequestLeavesBothPersistedBalancesAndTransactionsUnchanged() {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "100.00");
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipient.getUser().getEmail(), new BigDecimal("1.001"))))
                .isInstanceOf(InvalidTransferException.class);

        assertRejectedState(balancesBefore);
    }

    @Test
    void unknownRecipientLeavesPersistedBalanceAndTransactionsUnchanged() {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest("missing@example.com", new BigDecimal("10.00"))))
                .isInstanceOf(UserNotFoundException.class);

        assertRejectedState(balancesBefore);
    }

    @Test
    void selfTransferLeavesPersistedBalanceAndTransactionsUnchanged() {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(sender.getUser().getEmail(), new BigDecimal("10.00"))))
                .isInstanceOf(SelfTransferNotAllowedException.class);

        assertRejectedState(balancesBefore);
    }

    @Test
    void missingRecipientWalletLeavesSenderBalanceAndTransactionsUnchanged() {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        User recipient = persistUser("Recipient", "recipient");
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipient.getEmail(), new BigDecimal("10.00"))))
                .isInstanceOf(WalletNotFoundException.class);

        assertRejectedState(balancesBefore);
    }

    @Test
    void missingSenderWalletLeavesRecipientBalanceAndTransactionsUnchanged() {
        User sender = persistUser("Sender", "sender");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "100.00");
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getId(),
                new TransferRequest(recipient.getUser().getEmail(), new BigDecimal("10.00"))))
                .isInstanceOf(WalletNotFoundException.class);

        assertRejectedState(balancesBefore);
    }

    @Test
    void insufficientFundsLeaveBothPersistedBalancesAndTransactionsUnchanged() {
        Wallet sender = persistFundedWallet("Sender", "sender", "100.00");
        persistFundedWallet("Recipient", "recipient", "50.00");
        String recipientEmail = userRepository.findAll().stream()
                .filter(user -> !user.getId().equals(sender.getUser().getId()))
                .findFirst()
                .orElseThrow()
                .getEmail();
        List<Map<String, Object>> balancesBefore = persistedBalances();

        assertThatThrownBy(() -> transferService.transfer(
                sender.getUser().getId(),
                new TransferRequest(recipientEmail, new BigDecimal("100.01"))))
                .isInstanceOf(InsufficientBalanceException.class);

        assertRejectedState(balancesBefore);
    }

    private Wallet persistFundedWallet(String name, String emailPrefix, String balance) {
        User user = persistUser(name, emailPrefix);
        Wallet wallet = walletRepository.saveAndFlush(Wallet.create(user, FIXTURE_CLOCK));
        jdbcTemplate.update(
                "update paynest.wallets set balance = ? where id = ?",
                new BigDecimal(balance),
                wallet.getId());
        return wallet;
    }

    private User persistUser(String name, String emailPrefix) {
        return userRepository.saveAndFlush(User.create(
                name,
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque",
                FIXTURE_CLOCK));
    }

    private BigDecimal balance(Long walletId) {
        return jdbcTemplate.queryForObject(
                "select balance from paynest.wallets where id = ?",
                BigDecimal.class,
                walletId);
    }

    private List<Map<String, Object>> persistedBalances() {
        return jdbcTemplate.queryForList(
                "select id, balance from paynest.wallets order by id");
    }

    private void assertRejectedState(List<Map<String, Object>> balancesBefore) {
        assertThat(persistedBalances()).isEqualTo(balancesBefore);
        assertThat(transactionRepository.count()).isZero();
    }
}
