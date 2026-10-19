package com.paynest.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.paynest.auth.api.SignInRequest;
import com.paynest.auth.security.JwtProperties;
import com.paynest.support.TestDatabaseSupport;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.persistence.WalletRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
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
class SignInHttpIntegrationTests {

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

    @Autowired
    private JwtProperties jwtProperties;

    @org.springframework.beans.factory.annotation.Value("${paynest.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        TestDatabaseSupport.cleanApplicationData(jdbcTemplate);
    }

    @Test
    void validCanonicalCredentialsReturnOnlyTheMinimalVerifiableTokenContractWithoutWrites(
            CapturedOutput output
    ) throws Exception {
        String canonicalEmail = "signin." + UUID.randomUUID() + "@example.com";
        String password = "exact-sensitive-password";
        User user = persistUser(canonicalEmail, password);
        String storedHash = user.getPasswordHash();

        MvcResult result = mockMvc.perform(signIn(new SignInRequest(
                        "\u2003" + canonicalEmail.toUpperCase() + "\u00A0",
                        password)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.matchesPattern(".*Z")))
                .andExpect(jsonPath("$..email").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..roles").doesNotExist())
                .andExpect(jsonPath("$..wallet").doesNotExist())
                .andExpect(jsonPath("$..balance").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Instant responseExpiry = Instant.parse(response.get("expiresAt").asString());
        String accessToken = response.get("accessToken").asString();
        Jws<Claims> token = parse(accessToken);

        assertThat(token.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(token.getPayload()).hasSize(3);
        assertThat(token.getPayload().getSubject()).isEqualTo(user.getId().toString());
        assertThat(token.getPayload().getIssuedAt().toInstant().getNano()).isZero();
        assertThat(token.getPayload().getExpiration().toInstant()).isEqualTo(responseExpiry);
        assertThat(responseExpiry.minus(jwtProperties.ttl()))
                .isEqualTo(token.getPayload().getIssuedAt().toInstant());
        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isZero();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(storedHash);
        assertThat(output.getAll()).doesNotContain(
                accessToken,
                canonicalEmail,
                canonicalEmail.toUpperCase(),
                password,
                jwtSecret);
        assertThat(output.getAll()).contains("event=authentication outcome=success");
    }

    @Test
    void exactSeventyTwoCodePointPasswordIncludingEdgeSpacesIsNotTransformed() throws Exception {
        String password = " " + "\uD83D\uDE00".repeat(70) + " ";
        String email = "exact." + UUID.randomUUID() + "@example.com";
        persistUser(email, password);

        mockMvc.perform(signIn(new SignInRequest(email, password)))
                .andExpect(status().isOk());
        mockMvc.perform(signIn(new SignInRequest(email, password.strip())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isZero();
    }

    @Test
    void invalidRequestsReturnSafeValidationErrorsBeforeAnyDatabaseMutation(CapturedOutput output)
            throws Exception {
        int logStart = output.getAll().length();
        mockMvc.perform(signIn(new SignInRequest("not-an-email", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(6)))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signin"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(userRepository.count()).isZero();
        assertThat(walletRepository.count()).isZero();
        assertThat(output.getAll().substring(logStart))
                .contains("event=request outcome=rejected category=validation")
                .doesNotContain(
                        "event=transfer outcome=rejected category=validation",
                        "not-an-email",
                        "/api/auth/signin",
                        "HttpMessageNotReadableException");
    }

    @Test
    void unsupportedSigninMediaTypeReturnsSafeValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType("text/plain")
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signin"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(jsonPath("$..exception").doesNotExist())
                .andExpect(jsonPath("$..stackTrace").doesNotExist());
    }

    @Test
    void incompatibleAcceptHeaderStillReturnsTheSharedJsonErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signin"));
    }

    @Test
    void unknownEmailAndWrongPasswordReturnTheSameGenericSafeContract(CapturedOutput output)
            throws Exception {
        String knownEmail = "known." + UUID.randomUUID() + "@example.com";
        String unknownEmail = "unknown." + UUID.randomUUID() + "@example.com";
        String wrongPassword = "wrong-sensitive-password";
        persistUser(knownEmail, "right-sensitive-password");

        mockMvc.perform(signIn(new SignInRequest(unknownEmail, "anything-sensitive")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signin"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        mockMvc.perform(signIn(new SignInRequest(knownEmail, wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Authentication failed"))
                .andExpect(jsonPath("$.path").value("/api/auth/signin"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        assertThat(userRepository.count()).isOne();
        assertThat(walletRepository.count()).isZero();
        assertThat(output.getAll()).doesNotContain(
                knownEmail,
                unknownEmail,
                wrongPassword,
                "anything-sensitive",
                "right-sensitive-password",
                jwtSecret);
        assertThat(output.getAll())
                .contains("event=authentication outcome=failure category=invalid_credentials");
    }

    @Test
    void onlyPostSigninIsPublicAndOtherRoutesRemainDeniedByDefault(CapturedOutput output)
            throws Exception {
        int logStart = output.getAll().length();
        mockMvc.perform(get("/api/auth/signin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-private-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/auth/unlisted")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-private-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        assertThat(output.getAll().substring(logStart))
                .contains("event=authorization outcome=denied category=access_denied")
                .doesNotContain(
                        "malformed-private-token",
                        "/api/auth/signin",
                        "/api/auth/unlisted",
                        "AccessDeniedException");
    }

    private User persistUser(String email, String password) {
        return userRepository.saveAndFlush(User.create(
                "Sign In User",
                email,
                passwordEncoder.encode(password),
                Clock.fixed(Instant.parse("2026-10-07T08:30:00Z"), ZoneOffset.UTC)));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signIn(
            SignInRequest request
    ) throws Exception {
        return post("/api/auth/signin")
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(request));
    }

    private Jws<Claims> parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
