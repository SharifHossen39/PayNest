package com.paynest;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.domain.Transaction;
import com.paynest.transaction.domain.TransactionStatus;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.user.domain.EmailNormalizer;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PayNestApplicationTests {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-07T08:30:00.123456789Z");
    private static final Instant PERSISTED_INSTANT = Instant.parse("2026-10-07T08:30:00.123456Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @BeforeEach
    void verifyDedicatedTestDatabase() {
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @Test
    void contextStartsAgainstMigratedPostgreSqlAndValidatesTheMappedSchema() {
        String databaseVersion = jdbcTemplate.queryForObject("show server_version", String.class);
        List<String> payNestTables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' order by table_name",
                String.class);
        Integer appliedVersionedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from paynest.flyway_schema_history where version is not null",
                Integer.class);

        assertThat(databaseVersion).startsWith("18.6");
        assertThat(payNestTables).containsExactly("flyway_schema_history", "transactions", "users", "wallets");
        assertThat(appliedVersionedMigrations).isEqualTo(3);
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.default-schema")).isEqualTo("paynest");
        assertThat(environment.getProperty("spring.flyway.schemas")).isEqualTo("paynest");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema"))
                .isEqualTo("paynest");
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
                .isEqualTo("paynest_test");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo(System.getenv("PAYNEST_TEST_DB_URL"));
        assertThat(environment.getProperty("spring.datasource.username"))
                .isEqualTo(System.getenv("PAYNEST_TEST_DB_USERNAME"));
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @Transactional
    void repositoriesRoundTripAllThreeEntitiesWithLazyOwnershipAndExactValues() {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(User.create(
                "  Ada Lovelace  ", " ADA+" + suffix + "@EXAMPLE.COM ", "{hash}opaque", FIXED_CLOCK));
        Wallet wallet = walletRepository.saveAndFlush(Wallet.create(user, FIXED_CLOCK));
        User receiverUser = userRepository.saveAndFlush(User.create(
                "Grace Hopper", "grace+" + suffix + "@example.com", "{hash}opaque", FIXED_CLOCK));
        Wallet receiverWallet = walletRepository.saveAndFlush(Wallet.create(receiverUser, FIXED_CLOCK));
        Transaction transaction = transactionRepository.saveAndFlush(Transaction.completed(
                wallet, receiverWallet, new BigDecimal("12.30"), FIXED_CLOCK));

        Long userId = user.getId();
        Long walletId = wallet.getId();
        Long transactionId = transaction.getId();
        entityManager.clear();

        Transaction reloadedTransaction = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(Persistence.getPersistenceUtil().isLoaded(reloadedTransaction, "senderWallet")).isFalse();
        assertThat(Persistence.getPersistenceUtil().isLoaded(reloadedTransaction, "receiverWallet")).isFalse();

        Wallet reloadedWallet = walletRepository.findByUserId(userId).orElseThrow();

        assertThat(Persistence.getPersistenceUtil().isLoaded(reloadedWallet, "user")).isFalse();
        User reloadedUser = userRepository.findByEmail("ada+" + suffix + "@example.com").orElseThrow();
        assertThat(reloadedUser.getId()).isEqualTo(userId);
        assertThat(reloadedUser.getName()).isEqualTo("Ada Lovelace");
        assertThat(reloadedUser.getPasswordHash()).isEqualTo("{hash}opaque");
        assertThat(reloadedUser.getCreatedAt()).isEqualTo(PERSISTED_INSTANT);
        assertThat(reloadedWallet.getId()).isEqualTo(walletId);
        assertThat(reloadedWallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(reloadedWallet.getBalance().scale()).isEqualTo(2);
        assertThat(reloadedWallet.getCurrency()).isEqualTo("USD");
        assertThat(reloadedWallet.getCreatedAt()).isEqualTo(PERSISTED_INSTANT);
        assertThat(reloadedWallet.getUser().getId()).isEqualTo(userId);
        assertThat(reloadedTransaction.getAmount()).isEqualByComparingTo("12.30");
        assertThat(reloadedTransaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(reloadedTransaction.getCreatedAt()).isEqualTo(PERSISTED_INSTANT);
        assertThat(reloadedTransaction.getSenderWallet().getId()).isEqualTo(walletId);
        assertThat(reloadedTransaction.getReceiverWallet().getId()).isEqualTo(receiverWallet.getId());

        reloadedWallet.credit(new BigDecimal("12.30"));
        reloadedWallet.debit(new BigDecimal("2.05"));
        walletRepository.saveAndFlush(reloadedWallet);
        entityManager.clear();

        Wallet mutatedWallet = walletRepository.findByUserId(userId).orElseThrow();
        assertThat(mutatedWallet.getBalance()).isEqualByComparingTo("10.25");
        assertThat(mutatedWallet.getBalance().scale()).isEqualTo(2);
    }

    @Test
    void namedDatabaseConstraintsRejectDuplicateAndInvalidRows() {
        String suffix = UUID.randomUUID().toString();
        String email = EmailNormalizer.normalize("  CONSTRAINT-" + suffix + "@EXAMPLE.COM  ");
        String duplicateEmail = EmailNormalizer.normalize("constraint-" + suffix + "@example.com");
        assertThat(duplicateEmail).isEqualTo(email);
        Long userId = insertUser(email);

        assertThatThrownBy(() -> insertUser(duplicateEmail))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_users_email");

        insertWallet(userId, BigDecimal.ZERO, "USD");
        assertThatThrownBy(() -> insertWallet(userId, BigDecimal.ZERO, "USD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_wallets_user");
        assertThatThrownBy(() -> insertWallet(userId + 999_999L, BigDecimal.ZERO, "USD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("fk_wallets_user");

        Long anotherUserId = insertUser("currency-" + UUID.randomUUID() + "@example.com");
        assertThatThrownBy(() -> insertWallet(anotherUserId, BigDecimal.ZERO, "EUR"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_wallets_usd_currency");

        Long thirdUserId = insertUser("balance-" + UUID.randomUUID() + "@example.com");
        assertThatThrownBy(() -> insertWallet(thirdUserId, new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_wallets_non_negative_balance");

        Long nanUserId = insertUser("nan-" + UUID.randomUUID() + "@example.com");
        assertThatThrownBy(() -> insertNaNWallet(nanUserId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("ck_wallets_non_negative_balance");
    }

    private Long insertUser(String email) {
        return jdbcTemplate.queryForObject(
                "insert into paynest.users(name, email, password_hash, created_at) "
                        + "values (?, ?, ?, ?) returning id",
                Long.class,
                "Constraint User",
                email,
                "{hash}opaque",
                Timestamp.from(PERSISTED_INSTANT));
    }

    private void insertWallet(Long userId, BigDecimal balance, String currency) {
        jdbcTemplate.update(
                "insert into paynest.wallets(user_id, balance, currency, created_at) values (?, ?, ?, ?)",
                userId,
                balance,
                currency,
                Timestamp.from(PERSISTED_INSTANT));
    }

    private void insertNaNWallet(Long userId) {
        jdbcTemplate.update(
                "insert into paynest.wallets(user_id, balance, currency, created_at) "
                        + "values (?, 'NaN'::numeric, 'USD', ?)",
                userId,
                Timestamp.from(PERSISTED_INSTANT));
    }
}
