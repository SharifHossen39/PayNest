package com.paynest.common.error;

import java.time.Clock;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler({InvalidSignUpException.class, InvalidSignInException.class})
    ResponseEntity<ApiError> handleValidation(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = exception instanceof InvalidSignUpException signUp
                ? signUp.getFieldErrors()
                : ((InvalidSignInException) exception).getFieldErrors();
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content type is not supported",
                request,
                Map.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> handleDuplicateEmail(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "An account with this email already exists",
                request,
                Map.of());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> handleAuthenticationFailed(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Authentication failed",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedFailure(HttpServletRequest request) {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "The request could not be completed",
                request,
                Map.of());
    }

    private ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        ApiError body = new ApiError(
                clock.instant(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
