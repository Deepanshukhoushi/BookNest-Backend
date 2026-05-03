package com.booknest.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Global exception handler that captures and formats errors for all REST controllers.
 * Ensures consistent error responses across the auth-service.
 *
 * <p>Status code contract:
 * <ul>
 *   <li>400 — validation failures ({@link MethodArgumentNotValidException}) and
 *       known business-rule violations ({@link IllegalArgumentException})</li>
 *   <li>404 — resource not found ({@link java.util.NoSuchElementException})</li>
 *   <li>4xx/5xx — explicit Spring status exceptions pass through their own code</li>
 *   <li>500 — unexpected {@link RuntimeException} or {@link Exception}</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handles @Valid DTO field-level validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR");
    }

    // Handles explicitly coded HTTP-status exceptions (401, 403, 404 thrown in service/controller)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return build(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, ex.getReason(), "AUTH_ERROR");
    }

    // Handles known, predictable business-rule violations (wrong password, duplicate account, etc.)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BUSINESS_ERROR");
    }

    // Handles resource-not-found scenarios raised via Optional.orElseThrow()
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(java.util.NoSuchElementException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
        return build(HttpStatus.NOT_FOUND, msg, "NOT_FOUND");
    }

    // Catches unexpected runtime exceptions — server-side bugs, return 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Internal server error",
                "INTERNAL_ERROR");
    }

    // Fallback for any unhandled checked exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", "INTERNAL_ERROR");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String errorCode) {
        return new ResponseEntity<>(new ErrorResponse(false, message, errorCode), status);
    }
}
