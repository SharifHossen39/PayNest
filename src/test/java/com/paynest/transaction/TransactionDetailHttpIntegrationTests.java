package com.paynest.transaction;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.paynest.auth.security.JwtService;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.persistence.TransactionRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class TransactionDetailHttpIntegrationTests {

    private static final Clock FIXTURE_CLOCK = Clock.fixed(
            Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
    private static final Instant TRANSACTION_TIME = Instant.parse("2026-10-15T10:00:00Z");

    @Autowired private WebApplicationContext applicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private TransactionRepository transactionRepository;

    private MockMvc mockMvc;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity()).build();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
        SecurityContextHolder.clearContext();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void senderReceivesExactSafeParticipantProtectedDetail() throws Exception {
        Wallet sender = persistWallet("Detail Sender", "detail-sender");
        Wallet recipient = persistWallet("Detail Recipient", "detail-recipient");
        long id = insertTransaction(sender, recipient, "12.34", TRANSACTION_TIME);

        mockMvc.perform(authorizedGet(sender, Long.toString(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(8)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.direction").value("SENT"))
                .andExpect(jsonPath("$.sender", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.sender.name").value("Detail Sender"))
                .andExpect(jsonPath("$.sender.email").value(sender.getUser().getEmail()))
                .andExpect(jsonPath("$.recipient", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.recipient.name").value("Detail Recipient"))
                .andExpect(jsonPath("$.recipient.email").value(recipient.getUser().getEmail()))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").value(TRANSACTION_TIME.toString()))
                .andExpect(jsonPath("$..walletId").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist());
    }

    @Test
    void receiverDirectionPreservesObjectiveRolesAndIgnoresCallerIdentity() throws Exception {
        Wallet sender = persistWallet("Objective Sender", "objective-sender");
        Wallet recipient = persistWallet("Objective Recipient", "objective-recipient");
        Wallet noise = persistWallet("Identity Noise", "identity-noise");
        long id = insertTransaction(sender, recipient, "56.78", TRANSACTION_TIME);

        mockMvc.perform(authorizedGet(recipient, Long.toString(id))
                        .queryParam("userId", noise.getUser().getId().toString())
                        .queryParam("walletId", noise.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(8)))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.direction").value("RECEIVED"))
                .andExpect(jsonPath("$.sender", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.sender.name").value("Objective Sender"))
                .andExpect(jsonPath("$.sender.email").value(sender.getUser().getEmail()))
                .andExpect(jsonPath("$.recipient", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.recipient.name").value("Objective Recipient"))
                .andExpect(jsonPath("$.recipient.email").value(recipient.getUser().getEmail()))
                .andExpect(jsonPath("$.amount").value(56.78))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").value(TRANSACTION_TIME.toString()))
                .andExpect(jsonPath("$..walletId").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist());
    }

    @Test
    void repositoryDetailQueryRejectsAnUnrelatedWallet() {
        Wallet sender = persistWallet("Repository Sender", "repository-sender");
        Wallet recipient = persistWallet("Repository Recipient", "repository-recipient");
        Wallet unrelated = persistWallet("Repository Stranger", "repository-stranger");
        long id = insertTransaction(sender, recipient, "42.00", TRANSACTION_TIME);

        assertThat(transactionRepository.findDetailByIdAndParticipant(id, unrelated.getId()))
                .isEmpty();
    }

    @Test
    void unknownAndUnrelatedTransactionsReturnSameSafeNotFoundContract() throws Exception {
        Wallet viewer = persistWallet("Protected Viewer", "protected-viewer");
        Wallet sender = persistWallet("Unrelated Sender", "unrelated-sender");
        Wallet recipient = persistWallet("Unrelated Recipient", "unrelated-recipient");
        long unrelatedId = insertTransaction(sender, recipient, "99.99", TRANSACTION_TIME);

        MvcResult unrelated = assertTransactionNotFound(viewer, unrelatedId);
        MvcResult unknown = assertTransactionNotFound(viewer, unrelatedId + 1_000_000L);
        assertThat(unrelated.getResponse().getContentAsString())
                .doesNotContain("Unrelated Sender", "Unrelated Recipient", "99.99");
        assertThat(unknown.getResponse().getContentAsString())
                .doesNotContain("Unrelated Sender", "Unrelated Recipient", "99.99");
    }

    @Test
    void invalidIdsFailBeforeWalletOrDetailQueriesAndLongMaxRemainsSupported() throws Exception {
        Wallet viewer = persistWallet("Validation Viewer", "validation-viewer");
        for (String id : new String[] {"0", "-1", "not-a-number", "1.5", "9223372036854775808"}) {
            statistics.clear();
            mockMvc.perform(authorizedGet(viewer, id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors.id").isString());
            assertThat(statistics.getPrepareStatementCount()).isOne();
        }
        statistics.clear();
        assertTransactionNotFound(viewer, Long.MAX_VALUE);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void walletlessAndUnauthenticatedRequestsReturnSafeErrors() throws Exception {
        User walletless = userRepository.saveAndFlush(User.create(
                "Walletless Detail Viewer",
                "walletless-detail." + UUID.randomUUID() + "@example.com",
                "{hash}opaque", FIXTURE_CLOCK));
        statistics.clear();
        mockMvc.perform(get("/api/transactions/1").header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(jwtService.issue(walletless.getId()).value())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        statistics.clear();
        assertUnauthorized(get("/api/transactions/1"), "/api/transactions/1");
        assertThat(statistics.getPrepareStatementCount()).isZero();

        statistics.clear();
        assertUnauthorized(
                get("/api/transactions/1").header(
                        HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"),
                "/api/transactions/1");
        assertThat(statistics.getPrepareStatementCount()).isZero();

        statistics.clear();
        assertUnauthorized(get("/api/transactions/not-a-number"),
                "/api/transactions/not-a-number");
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void successfulDetailUsesOneProtectedProjectionWithoutGraphFetches() throws Exception {
        Wallet sender = persistWallet("Query Sender", "query-sender");
        Wallet recipient = persistWallet("Query Recipient", "query-recipient");
        long id = insertTransaction(sender, recipient, "10.00", TRANSACTION_TIME);
        String token = jwtService.issue(sender.getUser().getId()).value();
        statistics.clear();

        mockMvc.perform(get("/api/transactions/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(statistics.getCollectionFetchCount()).isZero();
    }

    private MvcResult assertTransactionNotFound(Wallet viewer, long id) throws Exception {
        return mockMvc.perform(authorizedGet(viewer, Long.toString(id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Transaction not found"))
                .andExpect(jsonPath("$.path").value("/api/transactions/" + id))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andReturn();
    }

    private void assertUnauthorized(
            MockHttpServletRequestBuilder request,
            String expectedPath
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(
                        org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.path").value(expectedPath))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..sender").doesNotExist())
                .andExpect(jsonPath("$..recipient").doesNotExist())
                .andExpect(jsonPath("$..wallet").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..token").doesNotExist());
    }

    private MockHttpServletRequestBuilder authorizedGet(Wallet viewer, String id) {
        return get("/api/transactions/{id}", id).header(
                HttpHeaders.AUTHORIZATION,
                bearer(jwtService.issue(viewer.getUser().getId()).value()));
    }

    private Wallet persistWallet(String name, String emailPrefix) {
        User user = userRepository.saveAndFlush(User.create(
                name,
                emailPrefix + "." + UUID.randomUUID() + "@example.com",
                "{hash}opaque", FIXTURE_CLOCK));
        return walletRepository.saveAndFlush(Wallet.create(user, FIXTURE_CLOCK));
    }

    private long insertTransaction(Wallet sender, Wallet recipient, String amount, Instant createdAt) {
        return jdbcTemplate.queryForObject(
                "insert into paynest.transactions"
                        + "(sender_wallet_id, receiver_wallet_id, amount, status, created_at) "
                        + "values (?, ?, ?, 'COMPLETED', ?) returning id",
                Long.class, sender.getId(), recipient.getId(), new BigDecimal(amount),
                Timestamp.from(createdAt));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
