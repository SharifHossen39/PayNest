package com.paynest.common.error;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class ApiErrorResponseWriter {

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<ApiError> response(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        body(status, error, message, request, fieldErrors));
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                body(status, error, message, request, Map.of()));
    }

    private ApiError body(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        return new ApiError(
                clock.instant(),
                status.value(),
                error,
                message,
                publicPath(request.getRequestURI()),
                fieldErrors);
    }

    private static String publicPath(String requestUri) {
        StringBuilder path = new StringBuilder(requestUri.length());
        boolean pathParameter = false;
        for (int index = 0; index < requestUri.length(); index++) {
            char current = requestUri.charAt(index);
            if (current == ';') {
                pathParameter = true;
            } else if (current == '/') {
                pathParameter = false;
                path.append(current);
            } else if (!pathParameter) {
                path.append(current);
            }
        }
        return path.toString();
    }
}
