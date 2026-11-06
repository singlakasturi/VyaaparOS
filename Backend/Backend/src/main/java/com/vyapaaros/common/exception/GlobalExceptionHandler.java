package com.vyapaaros.common.exception;

import com.vyapaaros.auth.service.OtpRateLimitException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException exception
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation() {
        return response(HttpStatus.BAD_REQUEST, "Validation failed", List.of());
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleOtpRateLimit() {
        return response(
            HttpStatus.TOO_MANY_REQUESTS,
            "Unable to issue a verification code at this time. Please try again later.",
            List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied() {
        return response(HttpStatus.FORBIDDEN, "Access denied.", List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ResourceConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientStock(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
            Instant.now(), HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
            exception.getMessage(), List.of(),
            Map.of("code", "INSUFFICIENT_STOCK", "items", exception.items())
        ));
    }

    @ExceptionHandler(com.vyapaaros.ai.provider.AiProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleAiProvider(
        com.vyapaaros.ai.provider.AiProviderException exception
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        logger.error(
            "Unhandled exception while processing request: {}",
            exception.getClass().getName(),
            exception
        );
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.",
            List.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String message,
        List<String> details
    ) {
        return ResponseEntity.status(status).body(
            new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details,
                Map.of()
            )
        );
    }
}
