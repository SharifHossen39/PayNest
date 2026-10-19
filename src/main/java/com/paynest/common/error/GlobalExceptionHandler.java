package com.paynest.common.error;

import java.sql.SQLException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorResponseWriter errorWriter;

    public GlobalExceptionHandler(ApiErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @ExceptionHandler({
            InvalidSignUpException.class,
            InvalidSignInException.class,
            InvalidTransferException.class
    })
    ResponseEntity<ApiError> handleValidation(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors;
        if (exception instanceof InvalidSignUpException signUp) {
            fieldErrors = signUp.getFieldErrors();
        } else if (exception instanceof InvalidSignInException signIn) {
            fieldErrors = signIn.getFieldErrors();
        } else {
            fieldErrors = ((InvalidTransferException) exception).getFieldErrors();
        }
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest(HttpServletRequest request) {
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                Map.of());
    }

    @ExceptionHandler(InvalidPaginationException.class)
    ResponseEntity<ApiError> handleInvalidPagination(
            InvalidPaginationException exception,
            HttpServletRequest request
    ) {
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                exception.getFieldErrors());
    }

    @ExceptionHandler(InvalidTransactionIdException.class)
    ResponseEntity<ApiError> handleInvalidTransactionId(
            InvalidTransactionIdException exception,
            HttpServletRequest request
    ) {
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                exception.getFieldErrors());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ResponseEntity<ApiError> handleTransactionNotFound(HttpServletRequest request) {
        logRequestRejected("transaction_not_found");
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "TRANSACTION_NOT_FOUND",
                "Transaction not found",
                request,
                Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleParameterTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                Map.of(exception.getName(), "Value has an invalid format"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
        logRejected(request, "validation");
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                Map.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> handleDuplicateEmail(HttpServletRequest request) {
        LOGGER.warn("event=request outcome=conflict category=duplicate_email");
        return errorResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "An account with this email already exists",
                request,
                Map.of());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> handleAuthenticationFailed(HttpServletRequest request) {
        LOGGER.warn("event=authentication outcome=failure category=invalid_credentials");
        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                "Authentication failed",
                request,
                Map.of());
    }

    @ExceptionHandler({UserNotFoundException.class, WalletNotFoundException.class})
    ResponseEntity<ApiError> handleMissingTransferResource(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        boolean userMissing = exception instanceof UserNotFoundException;
        String category = userMissing ? "user_not_found" : "wallet_not_found";
        if (isTransferRequest(request)) {
            LOGGER.warn("event=transfer outcome=rejected category={}", category);
        } else {
            LOGGER.warn("event=request outcome=rejected category={}", category);
        }
        return errorResponse(
                HttpStatus.NOT_FOUND,
                userMissing ? "USER_NOT_FOUND" : "WALLET_NOT_FOUND",
                userMissing ? "User not found" : "Wallet not found",
                request,
                Map.of());
    }

    @ExceptionHandler({SelfTransferNotAllowedException.class, InsufficientBalanceException.class})
    ResponseEntity<ApiError> handleTransferBusinessRule(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        boolean selfTransfer = exception instanceof SelfTransferNotAllowedException;
        LOGGER.warn("event=transfer outcome=rejected category={}",
                selfTransfer ? "self_transfer" : "insufficient_balance");
        return errorResponse(
                HttpStatus.UNPROCESSABLE_CONTENT,
                selfTransfer ? "SELF_TRANSFER_NOT_ALLOWED" : "INSUFFICIENT_BALANCE",
                selfTransfer ? "Transfer is not allowed" : "Insufficient balance",
                request,
                Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> handleTransferPersistenceFailure(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        if (!isTransferRequest(request)) {
            LOGGER.error("event=request outcome=failure category=persistence");
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "The request could not be completed",
                    request,
                    Map.of());
        }
        if (hasSqlState(exception, "40P01") || hasSqlState(exception, "55P03")) {
            LOGGER.warn("event=transfer outcome=conflict category=concurrency");
            return errorResponse(
                    HttpStatus.CONFLICT,
                    "CONFLICT",
                    "The transfer conflicted with another request",
                    request,
                    Map.of());
        }
        LOGGER.error("event=transfer outcome=failure category=persistence");
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TRANSACTION_FAILED",
                "The transfer could not be completed",
                request,
                Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedFailure(HttpServletRequest request) {
        if (isTransferRequest(request)) {
            LOGGER.error("event=transfer outcome=failure category=unexpected");
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSACTION_FAILED",
                    "The transfer could not be completed",
                    request,
                    Map.of());
        }
        LOGGER.error("event=request outcome=failure category=unexpected");
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
        return errorWriter.response(status, error, message, request, fieldErrors);
    }

    private static boolean hasSqlState(Throwable failure, String expectedState) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTransferRequest(HttpServletRequest request) {
        return (request.getContextPath() + "/api/wallet/transfers")
                .equals(request.getRequestURI());
    }

    private static void logRequestRejected(String category) {
        LOGGER.warn("event=request outcome=rejected category={}", category);
    }

    private static void logRejected(HttpServletRequest request, String category) {
        if (isTransferRequest(request)) {
            LOGGER.warn("event=transfer outcome=rejected category={}", category);
        } else {
            logRequestRejected(category);
        }
    }
}
