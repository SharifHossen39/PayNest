package com.paynest.transaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.domain.Transaction;
import com.paynest.transaction.domain.TransactionStatus;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import jakarta.persistence.Persistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TransactionPersistenceIntegrationTests {

    private static final Instant SOURCE_INSTANT = Instant.parse("2026-10-14T09:45:12.123456789Z");
    private static final Instant STORED_INSTANT = Instant.parse("2026-10-14T09:45:12.123456Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(SOURCE_INSTANT, ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanGuardedDatabase() {
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @Test
    void savesAndReloadsAnExactCompletedRecordWithLazyParticipants() {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        SavedIds savedIds = Objects.requireNonNull(transactions.execute(status -> {
            Wallet sender = persistWallet("Sender", "sender");
            Wallet receiver = persistWallet("Receiver", "receiver");
            Transaction saved = transactionRepository.saveAndFlush(Transaction.completed(
                    sender, receiver, new BigDecimal("125.50"), FIXED_CLOCK));
            return new SavedIds(saved.getId(), sender.getId(), receiver.getId());
        }));

        transactions.executeWithoutResult(status -> {
            Transaction reloaded = transactionRepository.findById(savedIds.transactionId()).orElseThrow();

            assertThat(reloaded.getId()).isEqualTo(savedIds.transactionId());
            assertThat(Persistence.getPersistenceUtil().isLoaded(reloaded, "senderWallet")).isFalse();
            assertThat(Persistence.getPersistenceUtil().isLoaded(reloaded, "receiverWallet")).isFalse();
            assertThat(reloaded.getAmount()).isEqualByComparingTo("125.50");
            assertThat(reloaded.getAmount().scale()).isEqualTo(2);
            assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(reloaded.getCreatedAt()).isEqualTo(STORED_INSTANT);
            assertThat(reloaded.getSenderWallet().getId()).isEqualTo(savedIds.senderId());
            assertThat(reloaded.getReceiverWallet().getId()).isEqualTo(savedIds.receiverId());
        });
    }

    @Test
    void namedParticipantConstraintsRejectMissingUnknownAndIdenticalWallets() {
        Long senderId = insertWallet("raw-sender");
        Long receiverId = insertWallet("raw-receiver");

        assertRejected("sender_wallet_id", () -> insertRaw(
                null, receiverId, "1.00", "COMPLETED", STORED_INSTANT));
        assertRejected("receiver_wallet_id", () -> insertRaw(
                senderId, null, "1.00", "COMPLETED", STORED_INSTANT));
        assertRejected("fk_transactions_sender_wallet", () -> insertRaw(
                senderId + 999_999L, receiverId, "1.00", "COMPLETED", STORED_INSTANT));
        assertRejected("fk_transactions_receiver_wallet", () -> insertRaw(
                senderId, receiverId + 999_999L, "1.00", "COMPLETED", STORED_INSTANT));
        assertRejected("ck_transactions_distinct_wallets", () -> insertRaw(
                senderId, senderId, "1.00", "COMPLETED", STORED_INSTANT));
    }

    @Test
    void amountAndStatusConstraintsRejectEveryRawInvalidRepresentableValue() {
        Long senderId = insertWallet("raw-sender");
        Long receiverId = insertWallet("raw-receiver");

        assertRejected("amount", () -> insertRaw(
                senderId, receiverId, null, "COMPLETED", STORED_INSTANT));
        assertRejected("ck_transactions_positive_amount", () -> insertRaw(
                senderId, receiverId, "0.00", "COMPLETED", STORED_INSTANT));
        assertRejected("ck_transactions_positive_amount", () -> insertRaw(
                senderId, receiverId, "-0.01", "COMPLETED", STORED_INSTANT));
        assertRejected("ck_transactions_positive_amount", () -> insertRaw(
                senderId, receiverId, "NaN", "COMPLETED", STORED_INSTANT));
        assertRejected(null, () -> insertRaw(
                senderId, receiverId, "100000000000000000.00", "COMPLETED", STORED_INSTANT));
        assertRejected("status", () -> insertRaw(
                senderId, receiverId, "1.00", null, STORED_INSTANT));
        assertRejected("ck_transactions_completed_status", () -> insertRaw(
                senderId, receiverId, "1.00", "PENDING", STORED_INSTANT));
        assertRejected("created_at", () -> insertRaw(
                senderId, receiverId, "1.00", "COMPLETED", null));
    }

    @Test
    void walletForeignKeysRejectParticipantDeletionAndPreserveTheCompletedRecord() {
        Long senderId = insertWallet("delete-sender");
        Long receiverId = insertWallet("delete-receiver");
        insertRaw(senderId, receiverId, "1.00", "COMPLETED", STORED_INSTANT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from paynest.wallets where id = ?", senderId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_transactions_sender_wallet");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from paynest.wallets where id = ?", receiverId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_transactions_receiver_wallet");

        assertThat(transactionRepository.count()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from paynest.wallets where id in (?, ?)",
                Integer.class,
                senderId,
                receiverId)).isEqualTo(2);
    }

    private Wallet persistWallet(String name, String emailPrefix) {
        User user = userRepository.saveAndFlush(User.create(
                name,
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque",
                FIXED_CLOCK));
        return walletRepository.saveAndFlush(Wallet.create(user, FIXED_CLOCK));
    }

    private Long insertWallet(String emailPrefix) {
        Long userId = jdbcTemplate.queryForObject(
                "insert into paynest.users(name, email, password_hash, created_at) "
                        + "values (?, ?, ?, ?) returning id",
                Long.class,
                "Raw Constraint User",
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque",
                Timestamp.from(STORED_INSTANT));
        return jdbcTemplate.queryForObject(
                "insert into paynest.wallets(user_id, created_at) values (?, ?) returning id",
                Long.class,
                userId,
                Timestamp.from(STORED_INSTANT));
    }

    private void insertRaw(
            Long senderWalletId,
            Long receiverWalletId,
            String amount,
            String status,
            Instant createdAt
    ) {
        jdbcTemplate.update(
                "insert into paynest.transactions"
                        + "(sender_wallet_id, receiver_wallet_id, amount, status, created_at) "
                        + "values (?, ?, ?::numeric, ?, ?)",
                senderWalletId,
                receiverWalletId,
                amount,
                status,
                createdAt == null ? null : Timestamp.from(createdAt));
    }

    private void assertRejected(String expectedDatabaseMessage, Runnable insert) {
        var assertion = assertThatThrownBy(insert::run)
                .isInstanceOf(DataIntegrityViolationException.class);
        if (expectedDatabaseMessage != null) {
            assertion.rootCause().hasMessageContaining(expectedDatabaseMessage);
        }
        assertThat(transactionRepository.count()).isZero();
    }

    private record SavedIds(Long transactionId, Long senderId, Long receiverId) {
    }
}
