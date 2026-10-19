package com.paynest.auth.security;

import java.io.IOException;

import com.paynest.common.error.ApiErrorResponseWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

@Component
public final class JsonRequestRejectedHandler implements RequestRejectedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonRequestRejectedHandler.class);

    private final ApiErrorResponseWriter errorWriter;

    public JsonRequestRejectedHandler(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestRejectedException requestRejectedException
    ) throws IOException, ServletException {
        LOGGER.warn("event=request outcome=rejected category=invalid_syntax");
        errorWriter.write(
                request,
                response,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed");
    }
}
