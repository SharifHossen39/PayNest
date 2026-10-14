package com.paynest.transfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import com.paynest.auth.security.JwtService;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class TransferHttpIntegrationTests {

    private static final Clock FIXTURE_CLOCK = Clock.fixed(
            Instant.parse("2026-10-14T08:00:00Z"), ZoneOffset.UTC);
    private static final BigDecimal FAILURE_TRIGGER_AMOUNT = new BigDecimal("18473.29");
    private static final String FAILURE_TRIGGER_MESSAGE = "private transfer insert failure";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        removeFailureTrigger();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        removeFailureTrigger();
    }

    @Test
    void validJwtCreatesTransferForOnlyItsSubjectAndReturnsExactNestedContract() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "100.00");

        mockMvc.perform(transferRequest(sender, recipient.getUser().getEmail(), "150.00")
                        .queryParam("senderId", recipient.getUser().getId().toString())
                        .queryParam("walletId", recipient.getId().toString())
                        .header("X-User-Id", recipient.getUser().getId())
                        .header("X-Wallet-Id", recipient.getId()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.transaction", org.hamcrest.Matchers.aMapWithSize(7)))
                .andExpect(jsonPath("$.transaction.id").isNumber())
                .andExpect(jsonPath("$.transaction.direction").value("SENT"))
                .andExpect(jsonPath("$.transaction.counterparty", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.transaction.counterparty.name").value("Recipient"))
                .andExpect(jsonPath("$.transaction.counterparty.email")
                        .value(recipient.getUser().getEmail()))
                .andExpect(jsonPath("$.transaction.amount").value(150.00))
                .andExpect(jsonPath("$.transaction.currency").value("USD"))
                .andExpect(jsonPath("$.transaction.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transaction.createdAt").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.balanceAfter").value(350.00))
                .andExpect(jsonPath("$..walletId").doesNotExist())
                .andExpect(jsonPath("$..senderId").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist());

        assertThat(balance(sender)).isEqualByComparingTo("350.00");
        assertThat(balance(recipient)).isEqualByComparingTo("250.00");
        assertThat(transactionCount()).isOne();
    }

    @Test
    void missingOrInvalidBearerReturnsSharedAuthenticationFailureWithoutWrites() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "500.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "100.00");

        assertUnauthorized(post("/api/wallet/transfers")
                .contentType("application/json")
                .content(requestJson(recipient.getUser().getEmail(), "10.00")));
        assertUnauthorized(post("/api/wallet/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                .contentType("application/json")
                .content(requestJson(recipient.getUser().getEmail(), "10.00")));

        assertThat(balance(sender)).isEqualByComparingTo("500.00");
        assertThat(balance(recipient)).isEqualByComparingTo("100.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void businessAndValidationFailuresUseStableSafeFourXxContracts() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "10.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "5.00");
        User recipientWithoutWallet = persistUser("No Wallet", "no-wallet");

        mockMvc.perform(transferRequest(sender, "not-an-email", "1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(6)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/wallet/transfers"))
                .andExpect(jsonPath("$.fieldErrors.recipientEmail").exists());
        assertError(
                transferRequest(sender, "missing@example.com", "1.00"),
                404, "USER_NOT_FOUND", "User not found");
        assertError(
                transferRequest(sender, recipientWithoutWallet.getEmail(), "1.00"),
                404, "WALLET_NOT_FOUND", "Wallet not found");
        assertError(
                transferRequest(sender, sender.getUser().getEmail(), "1.00"),
                422, "SELF_TRANSFER_NOT_ALLOWED", "Transfer is not allowed");
        assertError(
                transferRequest(sender, recipient.getUser().getEmail(), "10.01"),
                422, "INSUFFICIENT_BALANCE", "Insufficient balance");

        assertThat(balance(sender)).isEqualByComparingTo("10.00");
        assertThat(balance(recipient)).isEqualByComparingTo("5.00");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void heldWalletLockReturnsBoundedConflictWithoutRetryOrStateChange() throws Exception {
        Wallet sender = persistFundedWallet("Sender", "sender", "100.00");
        Wallet recipient = persistFundedWallet("Recipient", "recipient", "25.00");

        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            lockWallet(blocker, sender.getId());

            assertError(
                    transferRequest(sender, recipient.getUser().getEmail(), "10.00"),
                    409, "CONFLICT", "The transfer conflicted with another request");

            assertThat(balance(blocker, sender)).isEqualByComparingTo("100.00");
            assertThat(balance(blocker, recipient)).isEqualByComparingTo("25.00");
            assertThat(transactionCount(blocker)).isZero();
            blocker.rollback();
        }
    }

    @Test
    void unexpectedPersistenceFailureRollsBackBeforeSafeTransactionFailedResponse(
            CapturedOutput output
    ) throws Exception {
        installFailureTrigger();
        Wallet sender = persistFundedWallet("Sensitive Sender", "sensitive-sender", "20000.00");
        Wallet recipient = persistFundedWallet("Sensitive Recipient", "sensitive-recipient", "10.00");
        String request = requestJson(recipient.getUser().getEmail(), FAILURE_TRIGGER_AMOUNT.toPlainString());
        int logStart = output.getAll().length();

        assertError(
                post("/api/wallet/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                        .contentType("application/json")
                        .content(request),
                500, "TRANSACTION_FAILED", "The transfer could not be completed");

        String operationLogs = output.getAll().substring(logStart);
        assertThat(balance(sender)).isEqualByComparingTo("20000.00");
        assertThat(balance(recipient)).isEqualByComparingTo("10.00");
        assertThat(transactionCount()).isZero();
        assertThat(operationLogs).doesNotContain(
                sender.getUser().getEmail(),
                recipient.getUser().getEmail(),
                FAILURE_TRIGGER_AMOUNT.toPlainString(),
                FAILURE_TRIGGER_MESSAGE,
                "23514");
    }

    private MockHttpServletRequestBuilder transferRequest(
            Wallet sender,
            String recipientEmail,
            String amount
    ) {
        return post("/api/wallet/transfers")
                .header(HttpHeaders.AUTHORIZATION, bearer(sender))
                .contentType("application/json")
                .content(requestJson(recipientEmail, amount));
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        assertError(request, 401, "AUTHENTICATION_FAILED", "Authentication failed");
    }

    private void assertError(
            MockHttpServletRequestBuilder request,
            int statusCode,
            String error,
            String message
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/wallet/transfers"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist())
                .andExpect(jsonPath("$..sql").doesNotExist());
    }

    private Wallet persistFundedWallet(String name, String emailPrefix, String initialBalance) {
        User user = persistUser(name, emailPrefix);
        Wallet wallet = walletRepository.saveAndFlush(Wallet.create(user, FIXTURE_CLOCK));
        jdbcTemplate.update(
                "update paynest.wallets set balance = ? where id = ?",
                new BigDecimal(initialBalance),
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

    private String bearer(Wallet wallet) {
        return "Bearer " + jwtService.issue(wallet.getUser().getId()).value();
    }

    private static String requestJson(String recipientEmail, String amount) {
        return "{\"recipientEmail\":\"" + recipientEmail + "\",\"amount\":" + amount + "}";
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

    private static void lockWallet(Connection connection, long walletId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from paynest.wallets where id = ? for update")) {
            statement.setLong(1, walletId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
            }
        }
    }

    private void installFailureTrigger() {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("""
                create function paynest.test_reject_transfer_http() returns trigger
                language plpgsql as $$
                begin
                    if new.amount = %s then
                        raise exception '%s' using errcode = '23514';
                    end if;
                    return new;
                end
                $$
                """.formatted(FAILURE_TRIGGER_AMOUNT.toPlainString(), FAILURE_TRIGGER_MESSAGE));
        jdbcTemplate.execute("""
                create trigger test_reject_transfer_http before insert on paynest.transactions
                for each row execute function paynest.test_reject_transfer_http()
                """);
    }

    private void removeFailureTrigger() {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbcTemplate);
        jdbcTemplate.execute(
                "drop trigger if exists test_reject_transfer_http on paynest.transactions");
        jdbcTemplate.execute("drop function if exists paynest.test_reject_transfer_http()");
    }
}
