package com.paynest.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import com.paynest.common.error.ApiErrorResponseWriter;
import com.paynest.user.persistence.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class JwtAuthenticationFilterTests {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JsonAuthenticationEntryPoint entryPoint = mock(JsonAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(
                    jwtService,
                    userRepository,
                    entryPoint,
                    new ApiErrorResponseWriter(
                            Clock.fixed(
                                    Instant.parse("2026-10-19T08:00:00Z"),
                                    ZoneOffset.UTC),
                            objectMapper));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidCryptographicTokenIsRejectedBeforeAnyUserLookup() throws Exception {
        MockHttpServletRequest request = protectedRequest("Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.verifyUserId("invalid-token")).thenReturn(OptionalLong.empty());

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Rejected request must not reach the chain");
        });

        verify(userRepository, never()).existsById(any());
        verify(entryPoint).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void staleUserIdIsRejectedWithoutInstallingAuthentication() throws Exception {
        MockHttpServletRequest request = protectedRequest("Bearer valid-but-stale");
        when(jwtService.verifyUserId("valid-but-stale")).thenReturn(OptionalLong.of(99L));
        when(userRepository.existsById(99L)).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Stale identity must not reach the chain");
        });

        verify(entryPoint).commence(any(), any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenInstallsOnlyAnIdPrincipalWithNoCredentialsOrAuthorities() throws Exception {
        MockHttpServletRequest request = protectedRequest("Bearer valid-token");
        when(jwtService.verifyUserId("valid-token")).thenReturn(OptionalLong.of(7L));
        when(userRepository.existsById(7L)).thenReturn(true);
        AtomicReference<Authentication> observed = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                observed.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getPrincipal()).isEqualTo(new AuthenticatedUser(7L));
        assertThat(observed.get().getCredentials()).isNull();
        assertThat(observed.get().getAuthorities()).isEmpty();
        verifyNoInteractions(entryPoint);
    }

    @Test
    void exactPublicAuthRouteBypassesEvenAnInvalidBearerHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/signin");
        request.setServletPath("/api/auth/signin");
        request.addHeader("Authorization", "Bearer invalid-token");
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                invoked.set(true));

        assertThat(invoked.get()).isTrue();
        verifyNoInteractions(jwtService, userRepository, entryPoint);
    }

    @Test
    void deniedRouteBypassesBearerProcessingEvenWithMalformedCredentials() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/wallet");
        request.setServletPath("/api/wallet");
        request.addHeader("Authorization", "Bearer malformed-private-token");
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                invoked.set(true));

        assertThat(invoked.get()).isTrue();
        verifyNoInteractions(jwtService, userRepository, entryPoint);
    }

    @Test
    void userLookupPersistenceFailureReturnsSafeInternalErrorWithoutInvokingChain(
            CapturedOutput output
    ) throws Exception {
        MockHttpServletRequest request = protectedRequest("Bearer private-token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        long privateUserId = 918_273_645L;
        when(jwtService.verifyUserId("private-token-value")).thenReturn(
                OptionalLong.of(privateUserId));
        when(userRepository.existsById(privateUserId)).thenThrow(
                new DataAccessResourceFailureException("private database lookup detail"));

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Persistence failure must not reach the chain");
        });

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body.get("error").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("message").asString()).isEqualTo("The request could not be completed");
        assertThat(body.get("path").asString()).isEqualTo("/api/users/me");
        assertThat(response.getContentAsString())
                .doesNotContain(
                        "private-token-value",
                        Long.toString(privateUserId),
                        "private database lookup detail");
        assertThat(output.getAll())
                .contains("event=authentication outcome=failure category=persistence")
                .doesNotContain(
                        "private-token-value",
                        Long.toString(privateUserId),
                        "private database lookup detail",
                        "/api/users/me");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(entryPoint);
    }

    private MockHttpServletRequest protectedRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setServletPath("/api/users/me");
        request.addHeader("Authorization", authorization);
        return request;
    }
}
