package com.paynest.auth;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.paynest.auth.api.SignUpRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RegistrationHttpIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        removeFailureTrigger();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @AfterEach
    void removeFailureTrigger() {
        TestDatabaseSupport.verifyDedicatedTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("drop trigger if exists test_reject_wallet on paynest.wallets");
        jdbcTemplate.execute("drop function if exists paynest.test_reject_wallet()");
    }

    @Test
    void validUnauthenticatedSignupReturnsOnlySafeDtosAndPersistsAtomicPair() throws Exception {
        String password = "\uD83D\uDE00".repeat(64) + "Passw0rd";

        mockMvc.perform(signup(new SignUpRequest(
                        "\u2003Ada Lovelace\u00A0",
                        "\u2003ADA." + UUID.randomUUID() + "@EXAMPLE.COM\u00A0",
                        password)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.user", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$.wallet", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.user.email").value(org.hamcrest.Matchers.endsWith("@example.com")))
                .andExpect(jsonPath("$.user.createdAt").isString())
                .andExpect(jsonPath("$.wallet.balance").value(0.00))
                .andExpect(jsonPath("$.wallet.currency").value("USD"))
                .andExpect(jsonPath("$.wallet.createdAt").isString())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..token").doesNotExist());

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isOne();
        User storedUser = userRepository.findAll().getFirst();
        Wallet storedWallet = walletRepository.findByUserId(storedUser.getId()).orElseThrow();
        assertThat(storedUser.getName()).isEqualTo("Ada Lovelace");
        assertThat(storedUser.getEmail()).endsWith("@example.com");
        assertThat(storedUser.getPasswordHash()).startsWith("{pbkdf2@SpringSecurity_v5_8}");
        assertThat(passwordEncoder.matches(password, storedUser.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(password.substring(0, password.length() - 1) + "X",
                storedUser.getPasswordHash())).isFalse();
        assertThat(storedWallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(storedWallet.getCurrency()).isEqualTo("USD");
    }

    @Test
    void validSignupIgnoresAStrayInvalidBearerAndCreatesTheNormalUserWalletPair() throws Exception {
        String email = "stray-bearer." + UUID.randomUUID() + "@example.com";

        mockMvc.perform(signup(new SignUpRequest("Stray Bearer", email, "valid-password"))
                        .header("Authorization", "Bearer deliberately-invalid"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(2)))
                .andExpect(jsonPath("$.user", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.wallet", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.wallet.balance").value(0.00))
                .andExpect(jsonPath("$.wallet.currency").value("USD"));

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isOne();
    }

    @Test
    void invalidCanonicalRequestReturnsSafeFourHundredAndPersistsNothing() throws Exception {
        mockMvc.perform(signup(new SignUpRequest(
                        " \uD83D\uDE00 ",
                        " invalid ",
                        "\uD83D\uDE00".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(6)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist());

        assertThat(userRepository.count()).isZero();
        assertThat(walletRepository.count()).isZero();
    }

    @Test
    void malformedJsonAndOmittedRequiredFieldsReturnSafeValidationErrorsWithoutPersistence() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(6)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        assertThat(userRepository.count()).isZero();
        assertThat(walletRepository.count()).isZero();
    }

    @Test
    void nonSignupRouteAndNonPostSignupMethodAreDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
        mockMvc.perform(get("/api/auth/signup"))
                .andExpect(status().isForbidden());
    }

    @Test
    void equivalentStoredEmailReturnsTheStableConflictWithoutAnotherPair() throws Exception {
        String identity = "Case." + UUID.randomUUID() + "@Example.com";
        mockMvc.perform(signup(new SignUpRequest("First User", identity, "valid-password")))
                .andExpect(status().isCreated());

        mockMvc.perform(signup(new SignUpRequest(
                        "Second User", "\u2003" + identity.toUpperCase() + "\u00A0", "other-password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$..constraint").doesNotExist())
                .andExpect(jsonPath("$..sql").doesNotExist());

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isOne();
    }

    @Test
    void concurrentEquivalentRequestsCommitOnePairAndReturnOneConflict(CapturedOutput output) throws Exception {
        String email = "race." + UUID.randomUUID() + "@example.com";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> first = () -> performAfter(start,
                    new SignUpRequest("Race One", email.toUpperCase(), "password-one"));
            Callable<MvcResult> second = () -> performAfter(start,
                    new SignUpRequest("Race Two", "\u2003" + email + "\u00A0", "password-two"));
            List<Future<MvcResult>> futures = List.of(executor.submit(first), executor.submit(second));
            start.countDown();

            List<Integer> statuses = futures.stream()
                    .map(RegistrationHttpIntegrationTests::completedStatus)
                    .sorted()
                    .toList();

            assertThat(statuses).containsExactly(201, 409);
            MvcResult conflict = futures.stream()
                    .map(RegistrationHttpIntegrationTests::completedResult)
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(conflict.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"EMAIL_ALREADY_EXISTS\"")
                    .doesNotContain("uk_users_email", "SQLException", "stackTrace");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isOne();
        assertSensitiveFailureDetailsAbsent(
                output,
                email,
                "Race One",
                "Race Two",
                "password-one",
                "password-two",
                "uk_users_email",
                "duplicate key value",
                "Detail: Key",
                "insert into paynest.users",
                "SQLState",
                "PSQLException",
                "org.postgresql.util",
                "org.postgresql.util.PSQLException");
    }

    @Test
    void walletInsertFailureRollsBackFlushedUserAndReturnsSafeInternalError(CapturedOutput output) throws Exception {
        jdbcTemplate.execute("""
                create function paynest.test_reject_wallet() returns trigger
                language plpgsql as $$
                begin
                    if (select name from paynest.users where id = new.user_id) = 'Fail Wallet' then
                        raise exception 'test wallet rejection' using errcode = '23514';
                    end if;
                    return new;
                end
                $$
                """);
        jdbcTemplate.execute("""
                create trigger test_reject_wallet before insert on paynest.wallets
                for each row execute function paynest.test_reject_wallet()
                """);

        String email = "rollback." + UUID.randomUUID() + "@example.com";
        mockMvc.perform(signup(new SignUpRequest("Fail Wallet", email, "valid-password")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The request could not be completed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signup"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$..sql").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist());

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(userRepository.count()).isZero();
        assertThat(walletRepository.count()).isZero();
        assertSensitiveFailureDetailsAbsent(
                output,
                email,
                "Fail Wallet",
                "valid-password",
                "test wallet rejection",
                "PL/pgSQL",
                "insert into paynest.wallets",
                "SQLState",
                "PSQLException",
                "org.postgresql.util",
                "org.postgresql.util.PSQLException");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            SignUpRequest request
    ) throws Exception {
        return post("/api/auth/signup")
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(request));
    }

    private MvcResult performAfter(CountDownLatch start, SignUpRequest request) throws Exception {
        start.await();
        return mockMvc.perform(signup(request)).andReturn();
    }

    private static int completedStatus(Future<MvcResult> future) {
        return completedResult(future).getResponse().getStatus();
    }

    private static MvcResult completedResult(Future<MvcResult> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertSensitiveFailureDetailsAbsent(CapturedOutput output, String... values) {
        assertThat(output.getAll()).doesNotContain(values);
    }
}
