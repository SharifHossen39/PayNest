package com.paynest.auth.security;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import com.paynest.common.error.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                clock.instant(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Authentication failed",
                request.getRequestURI(),
                Map.of()));
    }
}
