package com.booknest.orderservice.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Global exception handler for the order-service.
 * Ensures consistent error response envelope across all endpoints.
 *
 * <p>Status code contract:
 * <ul>
 *   <li>400 — validation failures and known business-rule violations (bad payment, bad input)</li>
 *   <li>404 — resource not found</li>
 *   <li>409 — conflict states (insufficient balance, insufficient stock)</li>
 *   <li>502 — downstream service errors (Feign client failures)</li>
 *   <li>500 — unexpected runtime errors (server bugs)</li>
 * </ul>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handles domain-specific insufficient wallet balance
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_BALANCE");
    }

    // Handles domain-specific insufficient book stock
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_STOCK");
    }

    // Handles invalid payment method or payment verification failure
    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_PAYMENT");
    }

    // Handles @Valid DTO field-level validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR");
    }

    // Handles resource-not-found (e.g. order ID, address ID not found)
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

    // Handles illegal state transitions (e.g. cancelling an already-delivered order)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INVALID_STATE");
    }

    // Handles Feign client failures when calling downstream microservices
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(FeignException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return build(status, "Downstream service error", "DOWNSTREAM_ERROR");
    }

    // Handles Spring's ResponseStatusException (e.g. 403 Forbidden from enforceUserAccess)
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex) {
        return build((HttpStatus) ex.getStatusCode(), ex.getReason(), "ACCESS_ERROR");
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
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", "INTERNAL_ERROR");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String errorCode) {
        ErrorResponse error = new ErrorResponse(false, message, errorCode);
        return new ResponseEntity<>(error, status);
    }
}
