package com.paynest.wallet;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.paynest.auth.security.JwtService;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.transaction.domain.Transaction;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManagerFactory;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CurrentWalletHttpIntegrationTests {

    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LXBheW5lc3Qtand0LXNlY3JldC0zMmI=";
    private static final SecretKey TEST_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokensReturnOnlyEachOwnersExactThreeFieldWalletWithoutWrites() throws Exception {
        Wallet first = persistWallet("Owner One", "owner-one", "2026-10-07T08:30:00Z");
        first.credit(new BigDecimal("137.42"));
        walletRepository.saveAndFlush(first);
        Wallet second = persistWallet("Owner Two", "owner-two", "2026-10-07T09:45:00Z");
        List<Map<String, Object>> usersBefore = persistedUserRows();
        List<Map<String, Object>> walletsBefore = persistedWalletRows();

        assertWallet(first, jwtService.issue(first.getUser().getId()).value());
        assertWallet(second, jwtService.issue(second.getUser().getId()).value());

        assertThat(persistedUserRows()).isEqualTo(usersBefore);
        assertThat(persistedWalletRows()).isEqualTo(walletsBefore);
    }

    @Test
    void callerSuppliedIdentityNoiseCannotOverrideTheVerifiedOwner() throws Exception {
        Wallet verified = persistWallet("Verified Owner", "verified-owner", "2026-10-07T08:30:00Z");
        Wallet other = persistWallet("Other Owner", "other-owner", "2026-10-07T09:30:00Z");

        mockMvc.perform(get("/api/wallet")
                        .queryParam("userId", other.getUser().getId().toString())
                        .queryParam("walletId", other.getId().toString())
                        .header("X-User-Id", other.getUser().getId())
                        .header("X-Wallet-Id", other.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(jwtService.issue(verified.getUser().getId()).value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.createdAt").value(verified.getCreatedAt().toString()))
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist());
    }

    @Test
    void everyMissingMalformedInvalidExpiredOrStaleBearerReturnsTheSharedUnauthorizedEnvelope(
            CapturedOutput output
    ) throws Exception {
        Wallet wallet = persistWallet("Bearer Owner", "bearer-owner", "2026-10-07T08:30:00Z");
        long userId = wallet.getUser().getId();
        String valid = jwtService.issue(userId).value();
        String tampered = tamper(valid);
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "another-test-only-secret-with-32-bytes".getBytes(StandardCharsets.UTF_8));
        String wrongSignature = signedToken(
                Long.toString(userId), Instant.now().plusSeconds(600), wrongKey);
        String expired = signedToken(
                Long.toString(userId), Instant.now().minusSeconds(1), TEST_KEY);
        String stale = jwtService.issue(userId + 999_999L).value();

        assertUnauthorized(get("/api/wallet"));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, "Basic abc"));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, bearer(tampered)));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, bearer(wrongSignature)));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, bearer(expired)));
        assertUnauthorized(get("/api/wallet").header(HttpHeaders.AUTHORIZATION, bearer(stale)));

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isOne();
        assertThat(output.getAll()).doesNotContain(
                valid, tampered, wrongSignature, expired, stale, TEST_SECRET);
    }

    @Test
    void authenticatedUserWithoutWalletReceivesOnlyTheSafeInternalErrorEnvelope() throws Exception {
        User user = persistUser("Missing Wallet", "missing-wallet");
        List<Map<String, Object>> usersBefore = persistedUserRows();

        mockMvc.perform(get("/api/wallet")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issue(user.getId()).value())))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("The request could not be completed"))
                .andExpect(jsonPath("$.path").value("/api/wallet"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist())
                .andExpect(jsonPath("$..wallet").doesNotExist());

        assertThat(persistedUserRows()).isEqualTo(usersBefore);
        assertThat(walletRepository.count()).isZero();
    }

    @Test
    void wrongMethodsNestedWalletPathsAndUnlistedRoutesRemainDenied() throws Exception {
        Wallet wallet = persistWallet("Route Owner", "route-owner", "2026-10-07T08:30:00Z");
        String authorization = bearer(jwtService.issue(wallet.getUser().getId()).value());

        assertForbidden(post("/api/wallet").header(HttpHeaders.AUTHORIZATION, authorization));
        assertForbidden(put("/api/wallet").header(HttpHeaders.AUTHORIZATION, authorization));
        assertForbidden(get("/api/wallet/").header(HttpHeaders.AUTHORIZATION, authorization));
        assertForbidden(get("/api/wallet/fund").header(HttpHeaders.AUTHORIZATION, authorization));
        assertForbidden(get("/api/unlisted").header(HttpHeaders.AUTHORIZATION, authorization));
        assertMatrixParameterRejected(get("/api/wallet;owner=other")
                .header(HttpHeaders.AUTHORIZATION, authorization));
        assertMatrixParameterRejected(get("/api/wallet;jsessionid=untrusted")
                .header(HttpHeaders.AUTHORIZATION, authorization));

        assertForbidden(post("/api/wallet"));
        assertForbidden(put("/api/wallet"));
        assertForbidden(get("/api/wallet/"));
        assertForbidden(get("/api/wallet/fund"));
        assertForbidden(get("/api/unlisted"));
        assertMatrixParameterRejected(get("/api/wallet;owner=other"));
        assertMatrixParameterRejected(get("/api/wallet;jsessionid=untrusted"));
    }

    @Test
    void persistenceIncludesV3TransactionWhileTheHttpSurfaceRemainsUnchanged() {
        Integer migrations = jdbcTemplate.queryForObject(
                "select count(*) from paynest.flyway_schema_history where version is not null",
                Integer.class);
        List<String> domainTables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' and table_name <> 'flyway_schema_history' "
                        + "order by table_name",
                String.class);

        assertThat(migrations).isEqualTo(3);
        assertThat(domainTables).containsExactly("transactions", "users", "wallets");
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getName())
                .containsExactlyInAnyOrder(
                        Transaction.class.getName(), User.class.getName(), Wallet.class.getName());
        assertThat(productEndpoints()).containsExactlyInAnyOrder(
                "POST /api/auth/signup",
                "POST /api/auth/signin",
                "GET /api/users/me",
                "GET /api/wallet");
    }

    private void assertWallet(Wallet wallet, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallet")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.balance").value(wallet.getBalance().doubleValue()))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.createdAt").value(wallet.getCreatedAt().toString()))
                .andExpect(jsonPath("$..id").doesNotExist())
                .andExpect(jsonPath("$..user").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..token").doesNotExist())
                .andExpect(jsonPath("$..claims").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"balance\":" + wallet.getBalance().toPlainString());
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.path").value("/api/wallet"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void assertForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    private void assertMatrixParameterRejected(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    private List<String> productEndpoints() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> mappings =
                requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> isPayNestHandler(entry.getValue()))
                .filter(entry -> entry.getKey().getPatternValues().stream()
                        .anyMatch(path -> path.startsWith("/api/")))
                .toList();
        assertThat(mappings).hasSize(4);
        assertThat(mappings).allSatisfy(entry -> assertThat(
                entry.getKey().getMethodsCondition().getMethods()).isNotEmpty());
        return mappings.stream()
                .flatMap(entry -> endpointNames(entry.getKey()).stream())
                .toList();
    }

    private static boolean isPayNestHandler(HandlerMethod handler) {
        return handler.getBeanType().getPackageName().startsWith("com.paynest");
    }

    private static List<String> endpointNames(RequestMappingInfo mapping) {
        return mapping.getMethodsCondition().getMethods().stream()
                .flatMap(method -> mapping.getPatternValues().stream()
                        .filter(path -> path.startsWith("/api/"))
                        .map(path -> method.name() + " " + path))
                .toList();
    }

    private List<Map<String, Object>> persistedUserRows() {
        return jdbcTemplate.queryForList(
                "select id, name, email, password_hash, created_at from paynest.users order by id");
    }

    private List<Map<String, Object>> persistedWalletRows() {
        return jdbcTemplate.queryForList(
                "select id, user_id, balance, currency, created_at from paynest.wallets order by id");
    }

    private Wallet persistWallet(String name, String emailPrefix, String createdAt) {
        User user = persistUser(name, emailPrefix);
        return walletRepository.saveAndFlush(Wallet.create(
                user,
                Clock.fixed(Instant.parse(createdAt), ZoneOffset.UTC)));
    }

    private User persistUser(String name, String emailPrefix) {
        String email = emailPrefix + "." + UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(User.create(
                name,
                email,
                passwordEncoder.encode("stored-password"),
                Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneOffset.UTC)));
    }

    private String signedToken(String subject, Instant expiration, SecretKey key) {
        return Jwts.builder()
                .subject(subject)
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String tamper(String token) {
        int tamperAt = token.lastIndexOf('.') + 3;
        char replacement = token.charAt(tamperAt) == 'A' ? 'B' : 'A';
        return token.substring(0, tamperAt) + replacement + token.substring(tamperAt + 1);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
