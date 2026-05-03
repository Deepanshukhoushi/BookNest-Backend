package com.booknest.bookservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler for the book-service.
 * Ensures consistent error response envelope across all endpoints.
 *
 * <p>Status code contract:
 * <ul>
 *   <li>400 — validation failures and known business-rule violations</li>
 *   <li>404 — resource not found</li>
 *   <li>409 — data integrity conflicts (duplicate ISBN, insufficient stock)</li>
 *   <li>500 — unexpected runtime errors (server bugs)</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handles domain-specific stock-exhaustion business error
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(InsufficientStockException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_STOCK");
    }

    // Handles @Valid DTO field-level validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, details, "VALIDATION_ERROR");
    }

    // Handles database constraint violations (e.g. duplicate ISBN)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "Database constraint violation";
        if (ex.getMessage() != null && ex.getMessage().contains("isbn")) {
            message = "A book with this ISBN already exists.";
        }
        return build(HttpStatus.CONFLICT, message, "CONFLICT");
    }

    // Handles resource-not-found (e.g. book ID does not exist)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Resource not found";
        return build(HttpStatus.NOT_FOUND, msg, "NOT_FOUND");
    }

    // Handles known business-rule violations passed as IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BUSINESS_ERROR");
    }

    // Catches unexpected runtime exceptions — these are server bugs, return 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
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
        ErrorResponse error = new ErrorResponse(false, message, errorCode);
        return new ResponseEntity<>(error, status);
    }
}
