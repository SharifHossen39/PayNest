package com.paynest.transaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.paynest.auth.security.JwtService;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class TransactionHistoryHttpIntegrationTests {

    private static final Clock FIXTURE_CLOCK = Clock.fixed(
            Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
    private static final Instant SHARED_TRANSACTION_TIME =
            Instant.parse("2026-10-15T10:00:00Z");

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
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
        SecurityContextHolder.clearContext();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void defaultHistoryReturnsOnlyParticipantTransactionsWithSafeViewerRelativeFields() throws Exception {
        Wallet viewer = persistWallet("History Owner", "history-owner");
        Wallet firstCounterparty = persistWallet("First Counterparty", "first-counterparty");
        Wallet secondCounterparty = persistWallet("Second Counterparty", "second-counterparty");
        Wallet unrelated = persistWallet("Unrelated User", "unrelated-user");
        long sentId = insertTransaction(
                viewer, firstCounterparty, "12.34", SHARED_TRANSACTION_TIME);
        long receivedId = insertTransaction(
                secondCounterparty, viewer, "56.78", SHARED_TRANSACTION_TIME);
        insertTransaction(unrelated, firstCounterparty, "99.99", SHARED_TRANSACTION_TIME.plusSeconds(60));

        mockMvc.perform(authorizedGet(viewer, "/api/transactions")
                        .queryParam("userId", unrelated.getUser().getId().toString())
                        .queryParam("walletId", unrelated.getId().toString())
                        .queryParam("sort", "amount,asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(6)))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.content[0]", org.hamcrest.Matchers.aMapWithSize(7)))
                .andExpect(jsonPath("$.content[0].id").value(receivedId))
                .andExpect(jsonPath("$.content[0].direction").value("RECEIVED"))
                .andExpect(jsonPath("$.content[0].counterparty", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.content[0].counterparty.name").value("Second Counterparty"))
                .andExpect(jsonPath("$.content[0].counterparty.email")
                        .value(secondCounterparty.getUser().getEmail()))
                .andExpect(jsonPath("$.content[0].amount").value(56.78))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].createdAt").value(SHARED_TRANSACTION_TIME.toString()))
                .andExpect(jsonPath("$.content[1].id").value(sentId))
                .andExpect(jsonPath("$.content[1].direction").value("SENT"))
                .andExpect(jsonPath("$.content[1].counterparty.name").value("First Counterparty"))
                .andExpect(jsonPath("$.content[1].counterparty.email")
                        .value(firstCounterparty.getUser().getEmail()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$..walletId").doesNotExist())
                .andExpect(jsonPath("$..senderWallet").doesNotExist())
                .andExpect(jsonPath("$..receiverWallet").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist());
    }

    @Test
    void explicitPaginationReturnsAccurateFullFinalAndOutOfRangePages() throws Exception {
        Wallet viewer = persistWallet("Paged Owner", "paged-owner");
        Wallet counterparty = persistWallet("Paged Counterparty", "paged-counterparty");
        for (int index = 0; index < 22; index++) {
            insertTransaction(
                    index % 2 == 0 ? viewer : counterparty,
                    index % 2 == 0 ? counterparty : viewer,
                    Integer.toString(index + 1) + ".00",
                    SHARED_TRANSACTION_TIME.plusSeconds(index));
        }

        mockMvc.perform(authorizedGet(viewer, "/api/transactions")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(20)))
                .andExpect(jsonPath("$.content[0].amount").value(22.00))
                .andExpect(jsonPath("$.content[19].amount").value(3.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(authorizedGet(viewer, "/api/transactions")
                        .queryParam("page", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.content[0].amount").value(2.00))
                .andExpect(jsonPath("$.content[1].amount").value(1.00))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(authorizedGet(viewer, "/api/transactions")
                        .queryParam("page", "2")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void walletWithNoTransactionsReturnsAnExactEmptyPage() throws Exception {
        Wallet viewer = persistWallet("Empty History Owner", "empty-history-owner");

        mockMvc.perform(authorizedGet(viewer, "/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void inclusivePageSizeBoundariesAreAccepted() throws Exception {
        Wallet viewer = persistWallet("Boundary Owner", "boundary-owner");
        Wallet counterparty = persistWallet("Boundary Counterparty", "boundary-counterparty");
        insertTransaction(viewer, counterparty, "1.00", SHARED_TRANSACTION_TIME);
        insertTransaction(viewer, counterparty, "2.00", SHARED_TRANSACTION_TIME.plusSeconds(1));

        mockMvc.perform(authorizedGet(viewer, "/api/transactions").queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(authorizedGet(viewer, "/api/transactions").queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void invalidPaginationReturnsValidationErrorBeforeAnyHistoryRead() throws Exception {
        Wallet viewer = persistWallet("Validation Owner", "validation-owner");

        assertInvalidPagination(viewer, "page", "-1", "Page must be zero or greater");
        assertInvalidPagination(viewer, "page", "", "Value has an invalid format");
        assertInvalidPagination(viewer, "page", "not-a-number", "Value has an invalid format");
        assertInvalidPagination(viewer, "size", "0", "Size must be between 1 and 100");
        assertInvalidPagination(viewer, "size", "-1", "Size must be between 1 and 100");
        assertInvalidPagination(viewer, "size", "101", "Size must be between 1 and 100");
        assertInvalidPagination(viewer, "size", "", "Value has an invalid format");
        assertInvalidPagination(viewer, "size", "not-a-number", "Value has an invalid format");
    }

    @Test
    void missingOrInvalidJwtAndMissingCurrentWalletReturnSafeErrors() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$..content").doesNotExist());

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$..content").doesNotExist());

        User walletless = persistUser("Walletless User", "walletless-user");
        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(jwtService.issue(walletless.getId()).value())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Wallet not found"))
                .andExpect(jsonPath("$.path").value("/api/transactions"))
                .andExpect(jsonPath("$..content").doesNotExist())
                .andExpect(jsonPath("$..wallet").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist());
    }

    @Test
    void fullTwentyItemPageUsesAConstantFourSqlStatements() throws Exception {
        Wallet viewer = persistWallet("Query Count Owner", "query-count-owner");
        Wallet counterparty = persistWallet("Query Count Counterparty", "query-count-counterparty");
        for (int index = 0; index < 20; index++) {
            insertTransaction(
                    viewer,
                    counterparty,
                    Integer.toString(index + 1) + ".00",
                    SHARED_TRANSACTION_TIME.plusSeconds(index));
        }
        String token = jwtService.issue(viewer.getUser().getId()).value();
        statistics.clear();

        mockMvc.perform(get("/api/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(20)));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }

    @Test
    void participantQueryPlanUsesBothHistoryIndexesBelowALimit() {
        Wallet viewer = persistWallet("Plan Owner", "plan-owner");
        Wallet counterparty = persistWallet("Plan Counterparty", "plan-counterparty");
        for (int index = 0; index < 200; index++) {
            insertTransaction(
                    index % 2 == 0 ? viewer : counterparty,
                    index % 2 == 0 ? counterparty : viewer,
                    "1.00",
                    SHARED_TRANSACTION_TIME.plusSeconds(index));
        }
        Wallet unrelatedSender = persistWallet("Plan Unrelated Sender", "plan-unrelated-sender");
        Wallet unrelatedReceiver = persistWallet("Plan Unrelated Receiver", "plan-unrelated-receiver");
        jdbcTemplate.update("""
                insert into paynest.transactions
                    (sender_wallet_id, receiver_wallet_id, amount, status, created_at)
                select ?, ?, 1.00, 'COMPLETED', ?::timestamptz + sequence * interval '1 second'
                from generate_series(1, 1000) sequence
                """,
                unrelatedSender.getId(),
                unrelatedReceiver.getId(),
                Timestamp.from(SHARED_TRANSACTION_TIME.minusSeconds(2000)));
        jdbcTemplate.execute("analyze paynest.transactions");

        String plan = new TransactionTemplate(transactionManager).execute(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            return jdbcTemplate.queryForObject("""
                    explain (analyze, buffers, format json)
                    select history.id,
                           sender_wallet.id,
                           sender_user.name,
                           sender_user.email,
                           receiver_wallet.id,
                           receiver_user.name,
                           receiver_user.email,
                           history.amount,
                           history.status,
                           history.created_at
                    from paynest.transactions history
                    join paynest.wallets sender_wallet on sender_wallet.id = history.sender_wallet_id
                    join paynest.users sender_user on sender_user.id = sender_wallet.user_id
                    join paynest.wallets receiver_wallet on receiver_wallet.id = history.receiver_wallet_id
                    join paynest.users receiver_user on receiver_user.id = receiver_wallet.user_id
                    where history.sender_wallet_id = ? or history.receiver_wallet_id = ?
                    order by history.created_at desc, history.id desc
                    limit 20 offset 0
                    """, String.class, viewer.getId(), viewer.getId());
        });

        assertThat(plan)
                .containsPattern("\\\"Node Type\\\"\\s*:\\s*\\\"Limit\\\"")
                .containsPattern("\\\"Actual Rows\\\"\\s*:\\s*20")
                .contains("idx_transactions_sender_history")
                .contains("idx_transactions_receiver_history");
    }

    private void assertInvalidPagination(
            Wallet viewer,
            String parameter,
            String value,
            String expectedMessage
    ) throws Exception {
        String token = jwtService.issue(viewer.getUser().getId()).value();
        statistics.clear();

        mockMvc.perform(get("/api/transactions")
                        .queryParam(parameter, value)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/transactions"))
                .andExpect(jsonPath("$.fieldErrors", org.hamcrest.Matchers.aMapWithSize(1)))
                .andExpect(jsonPath("$.fieldErrors." + parameter).value(expectedMessage))
                .andExpect(jsonPath("$..content").doesNotExist())
                .andExpect(jsonPath("$..sql").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist());

        assertThat(statistics.getPrepareStatementCount()).isOne();
    }

    private MockHttpServletRequestBuilder authorizedGet(Wallet wallet, String path) {
        return get(path).header(
                HttpHeaders.AUTHORIZATION,
                bearer(jwtService.issue(wallet.getUser().getId()).value()));
    }

    private Wallet persistWallet(String name, String emailPrefix) {
        return walletRepository.saveAndFlush(Wallet.create(
                persistUser(name, emailPrefix), FIXTURE_CLOCK));
    }

    private User persistUser(String name, String emailPrefix) {
        return userRepository.saveAndFlush(User.create(
                name,
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque",
                FIXTURE_CLOCK));
    }

    private long insertTransaction(
            Wallet sender,
            Wallet receiver,
            String amount,
            Instant createdAt
    ) {
        return jdbcTemplate.queryForObject(
                "insert into paynest.transactions"
                        + "(sender_wallet_id, receiver_wallet_id, amount, status, created_at) "
                        + "values (?, ?, ?, 'COMPLETED', ?) returning id",
                Long.class,
                sender.getId(),
                receiver.getId(),
                new BigDecimal(amount),
                Timestamp.from(createdAt));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
