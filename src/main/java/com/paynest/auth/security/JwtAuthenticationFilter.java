package com.paynest.auth.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.OptionalLong;

import com.paynest.common.error.ApiErrorResponseWriter;
import com.paynest.user.persistence.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            ApiErrorResponseWriter errorWriter
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = applicationPath(request);
        String method = request.getMethod();
        return !("GET".equals(method) && "/api/users/me".equals(path)
                || "GET".equals(method) && "/api/wallet".equals(path)
                || "POST".equals(method) && "/api/wallet/transfers".equals(path)
                || "GET".equals(method) && "/api/transactions".equals(path)
                || "GET".equals(method) && isTransactionDetailPath(path));
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
        if (userId.isEmpty()) {
            reject(request, response);
            return;
        }
        boolean userExists;
        try {
            userExists = userRepository.existsById(userId.getAsLong());
        } catch (DataAccessException exception) {
            rejectPersistenceFailure(request, response);
            return;
        }
        if (!userExists) {
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

    private static boolean isTransactionDetailPath(String path) {
        String prefix = "/api/transactions/";
        return path.startsWith(prefix)
                && path.length() > prefix.length()
                && path.indexOf('/', prefix.length()) < 0;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication failed"));
    }

    private void rejectPersistenceFailure(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        SecurityContextHolder.clearContext();
        LOGGER.error("event=authentication outcome=failure category=persistence");
        errorWriter.write(
                request,
                response,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed");
    }
}
