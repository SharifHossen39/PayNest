package com.paynest.auth.security;

import java.io.IOException;

import com.paynest.common.error.ApiErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    private final ApiErrorResponseWriter errorWriter;

    public JsonAuthenticationEntryPoint(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        LOGGER.warn("event=authentication outcome=failure category=invalid_token");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "Authentication token is invalid");
    }
}
