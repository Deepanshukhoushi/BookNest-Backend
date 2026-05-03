package com.booknest.walletservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleInsufficientBalance_returns409() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleInsufficientBalance(new InsufficientBalanceException("No funds"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(resp.getBody().getMessage()).isEqualTo("No funds");
    }

    @Test
    void handleInvalidPayment_returns400() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleInvalidPayment(new InvalidPaymentException("Bad payment"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("INVALID_PAYMENT");
    }

    @Test
    void handleResponseStatus_mapsStatusCode() {
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");

        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("REQUEST_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void handleValidation_returns400WithFieldMessage() {
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(
                List.of(new FieldError("obj", "amount", "must be positive")));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getMessage()).contains("amount");
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleNotFound(new NoSuchElementException("Wallet not found"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleNotFound_nullMessage_usesDefault() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleNotFound(new NoSuchElementException((String) null));

        assertThat(resp.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad arg"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("BUSINESS_ERROR");
    }

    @Test
    void handleRuntime_returns500() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException("Crash"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Crash");
    }

    @Test
    void handleRuntime_nullMessage_usesDefault() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException((String) null));

        assertThat(resp.getBody().getMessage()).isEqualTo("Internal server error");
    }

    @Test
    void handleException_returns500() throws Exception {
        ResponseEntity<ErrorResponse> resp =
                handler.handleException(new Exception("Unexpected"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getMessage()).isEqualTo("Unexpected server error");
    }
}
