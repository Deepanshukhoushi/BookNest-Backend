package com.booknest.reviewservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler for the review-service.
 * Ensures consistent error response envelope across all endpoints.
 *
 * <p>Status code contract:
 * <ul>
 *   <li>400 — validation failures and known business-rule violations</li>
 *   <li>404 — review or book not found</li>
 *   <li>4xx/5xx — explicit Spring status exceptions pass through their own code</li>
 *   <li>500 — unexpected runtime errors (server bugs)</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handles explicitly coded HTTP-status exceptions (401, 403 thrown in controller)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return build(status, ex.getReason() != null ? ex.getReason() : ex.getMessage(), "REQUEST_ERROR");
    }

    // Handles @Valid DTO field-level validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR");
    }

    // Handles resource-not-found (review or book ID does not exist)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
        return build(HttpStatus.NOT_FOUND, msg, "NOT_FOUND");
    }

    // Handles known business-rule violations (e.g. user already reviewed this book)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BUSINESS_ERROR");
    }

    // Catches unexpected runtime exceptions — these are server bugs, return 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Internal server error",
                "INTERNAL_ERROR");
    }

    // Fallback for any unhandled checked exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandled(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", "INTERNAL_ERROR");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String errorCode) {
        return new ResponseEntity<>(new ErrorResponse(false, message, errorCode), status);
    }
}
