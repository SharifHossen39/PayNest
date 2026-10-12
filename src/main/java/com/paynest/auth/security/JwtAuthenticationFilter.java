package com.paynest.auth.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.OptionalLong;

import com.paynest.user.persistence.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            JsonAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = applicationPath(request);
        if ("POST".equals(request.getMethod())
                && ("/api/auth/signup".equals(path) || "/api/auth/signin".equals(path))) {
            return true;
        }
        return "/v3/api-docs".equals(path)
                || "/v3/api-docs.yaml".equals(path)
                || path.startsWith("/v3/api-docs/")
                || "/swagger-ui.html".equals(path)
                || "/swagger-ui".equals(path)
                || path.startsWith("/swagger-ui/");
    }

    private static String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? path : path.substring(contextPath.length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        List<String> headers = authorizationHeaders(request);
        if (headers.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = headers.size() == 1 ? bearerToken(headers.getFirst()) : null;
        OptionalLong userId = jwtService.verifyUserId(token);
        if (userId.isEmpty() || !userRepository.existsById(userId.getAsLong())) {
            reject(request, response);
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(userId.getAsLong());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        filterChain.doFilter(request, response);
    }

    private static List<String> authorizationHeaders(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(AUTHORIZATION);
        List<String> headers = new ArrayList<>();
        if (values != null) {
            values.asIterator().forEachRemaining(headers::add);
        }
        return headers;
    }

    private static String bearerToken(String header) {
        if (header == null
                || header.length() <= BEARER_PREFIX.length()
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length());
        return token.codePoints().anyMatch(Character::isWhitespace) ? null : token;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication failed"));
    }
}
