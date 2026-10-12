package com.paynest.auth.security;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import com.paynest.user.persistence.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JsonAuthenticationEntryPoint entryPoint = mock(JsonAuthenticationEntryPoint.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, userRepository, entryPoint);

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

    private MockHttpServletRequest protectedRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setServletPath("/api/users/me");
        request.addHeader("Authorization", authorization);
        return request;
    }
}
