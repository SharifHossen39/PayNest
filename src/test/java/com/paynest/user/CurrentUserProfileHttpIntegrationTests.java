package com.paynest.user;

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

import com.paynest.auth.api.SignInRequest;
import com.paynest.auth.security.JwtService;
import com.paynest.support.TestDatabaseSupport;
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
class CurrentUserProfileHttpIntegrationTests {

    private static final String TEST_SECRET =
            "dGVzdC1vbmx5LXBheW5lc3Qtand0LXNlY3JldC0zMmI=";
    private static final SecretKey TEST_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET));

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
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    void validTokensReturnOnlyEachOwnersFourFieldProfileWithoutWrites() throws Exception {
        User first = persistUser("Owner One", "owner-one");
        User second = persistUser("Owner Two", "owner-two");
        long userCount = userRepository.count();
        long walletCount = walletRepository.count();
        List<Map<String, Object>> usersBefore = persistedUserRows();

        assertProfile(first, jwtService.issue(first.getId()).value());
        assertProfile(second, jwtService.issue(second.getId()).value());

        assertThat(userRepository.count()).isEqualTo(userCount);
        assertThat(walletRepository.count()).isEqualTo(walletCount);
        assertThat(persistedUserRows()).isEqualTo(usersBefore);
    }

    @Test
    void callerSuppliedIdentityNoiseCannotOverrideTheVerifiedOwner() throws Exception {
        User first = persistUser("Verified Owner", "verified-owner");
        User second = persistUser("Other User", "other-user");
        String token = jwtService.issue(first.getId()).value();

        mockMvc.perform(get("/api/users/me")
                        .queryParam("userId", second.getId().toString())
                        .queryParam("id", second.getId().toString())
                        .header("X-User-Id", second.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$.id").value(first.getId()))
                .andExpect(jsonPath("$.name").value(first.getName()))
                .andExpect(jsonPath("$.email").value(first.getEmail()));
    }

    @Test
    void everyMissingMalformedExpiredOrStaleBearerReturnsTheSameSafeUnauthorizedEnvelope(
            CapturedOutput output
    ) throws Exception {
        User user = persistUser("Bearer User", "bearer-user");
        String valid = jwtService.issue(user.getId()).value();
        String tampered = tamper(valid);
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "another-test-only-secret-with-32-bytes".getBytes(StandardCharsets.UTF_8));
        Instant future = Instant.now().plusSeconds(600);
        String wrongSignature = signedToken(user.getId().toString(), future, wrongKey);
        String expired = signedToken(user.getId().toString(), Instant.now().minusSeconds(1), TEST_KEY);
        String missingSubject = Jwts.builder()
                .expiration(Date.from(future))
                .signWith(TEST_KEY, Jwts.SIG.HS256)
                .compact();
        String nonNumeric = signedToken("not-a-user", future, TEST_KEY);
        String overflow = signedToken("9223372036854775808", future, TEST_KEY);
        String stale = jwtService.issue(user.getId() + 999_999L).value();

        assertUnauthorized(get("/api/users/me"));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic abc"));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer "));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        assertUnauthorized(get("/api/users/me").header(
                HttpHeaders.AUTHORIZATION, bearer(valid), bearer(valid)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(wrongSignature)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(missingSubject)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(nonNumeric)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(overflow)));
        assertUnauthorized(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(stale)));

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isZero();
        assertThat(output.getAll()).doesNotContain(
                valid,
                tampered,
                wrongSignature,
                expired,
                missingSubject,
                nonNumeric,
                overflow,
                stale,
                TEST_SECRET);
    }

    @Test
    void repeatedRequestsRebuildAuthenticationWithoutCreatingAnHttpSession() throws Exception {
        User user = persistUser("Stateless User", "stateless-user");
        String token = jwtService.issue(user.getId()).value();

        MvcResult first = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(first.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        MvcResult second = mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(second.getRequest().getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerSchemeMatchingIsCaseInsensitive() throws Exception {
        User user = persistUser("Scheme User", "scheme-user");
        String token = jwtService.issue(user.getId()).value();

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "bEaReR " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    void publicRoutesIgnoreStrayBearerWhileUnlistedProductRoutesRemainDenied() throws Exception {
        String password = "public-route-password";
        User user = persistUser("Public Route User", "public-route", password);
        String invalidBearer = "Bearer deliberately-invalid";

        mockMvc.perform(post("/api/auth/signin")
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(
                                new SignInRequest(user.getEmail(), password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mockMvc.perform(get("/v3/api-docs.yaml")
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mockMvc.perform(get("/swagger-ui/index.html")
                        .header(HttpHeaders.AUTHORIZATION, invalidBearer))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mockMvc.perform(get("/api/unlisted"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/unlisted")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issue(user.getId()).value())))
                .andExpect(status().isForbidden());
        MvcResult logout = mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden())
                .andReturn();
        assertThat(logout.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    void databaseContractRemainsAtV2WithOnlyUserAndWalletPersistedTypes() {
        Integer migrations = jdbcTemplate.queryForObject(
                "select count(*) from paynest.flyway_schema_history where version is not null",
                Integer.class);
        List<String> domainTables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'paynest' and table_name <> 'flyway_schema_history' "
                        + "order by table_name",
                String.class);

        assertThat(migrations).isEqualTo(2);
        assertThat(domainTables).containsExactly("users", "wallets");
        assertThat(entityManagerFactory.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getName())
                .containsExactlyInAnyOrder(User.class.getName(), Wallet.class.getName());
    }

    private void assertProfile(User user, String token) throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value(user.getName()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.createdAt").value(user.getCreatedAt().toString()))
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..token").doesNotExist())
                .andExpect(jsonPath("$..claims").doesNotExist())
                .andExpect(jsonPath("$..wallet").doesNotExist());
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
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private List<Map<String, Object>> persistedUserRows() {
        return jdbcTemplate.queryForList(
                "select id, name, email, password_hash, created_at "
                        + "from paynest.users order by id");
    }

    private User persistUser(String name, String emailPrefix) {
        return persistUser(name, emailPrefix, "stored-password");
    }

    private User persistUser(String name, String emailPrefix, String password) {
        String email = emailPrefix + "." + UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(User.create(
                name,
                email,
                passwordEncoder.encode(password),
                Clock.fixed(Instant.parse("2026-10-07T08:30:00Z"), ZoneOffset.UTC)));
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
