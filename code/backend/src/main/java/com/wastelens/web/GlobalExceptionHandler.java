package com.wastelens.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.wastelens.web.exception.CapacityLimitException;
import com.wastelens.web.exception.IdempotencyConflictException;
import com.wastelens.web.exception.InvalidInputException;
import com.wastelens.web.exception.RateLimitException;
import com.wastelens.web.exception.ServiceUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(CapacityLimitException.class)
    public ResponseEntity<Map<String, Object>> handleCapacityLimit(CapacityLimitException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "ENTRY_LIMIT_REACHED", ex.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> error = Map.of(
            "code", "RATE_LIMITED",
            "message", ex.getMessage(),
            "requestId", requestId
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, "60")
            .body(Map.of("error", error));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(ServiceUnavailableException ex) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler({
        InvalidInputException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class,
        UnrecognizedPropertyException.class,
        JsonProcessingException.class
    })
    public ResponseEntity<Map<String, Object>> handleInvalidInput(Exception ex) {
        String msg = ex.getMessage();
        if (ex instanceof HttpMessageNotReadableException readableEx && readableEx.getCause() != null) {
            msg = readableEx.getCause().getMessage();
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_INPUT", msg != null ? msg : "Invalid input payload");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String code, String message) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> error = Map.of(
            "code", code,
            "message", message,
            "requestId", requestId
        );
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
