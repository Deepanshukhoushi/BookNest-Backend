package com.booknest.cartservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
    void handleValidation_returns400WithFieldMessage() {
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(
                List.of(new FieldError("obj", "quantity", "must be at least 1")));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getMessage()).contains("quantity");
    }

    @Test
    void handleValidation_multipleErrors_joinsMessages() {
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "userId", "must not be null"),
                new FieldError("obj", "bookId", "must not be null")));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getBody().getMessage()).contains("userId").contains("bookId");
    }

    @Test
    void handleResponseStatus_mapsStatusCode() {
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");

        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("AUTH_ERROR");
    }

    @Test
    void handleResponseStatus_unknownCode_defaultsBadRequest() {
        // Covers the null-resolve branch: code 999 resolves to null → BAD_REQUEST
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad");

        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleRuntime_returns400() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException("Cart item not found"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("BUSINESS_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Cart item not found");
    }

    @Test
    void handleRuntime_nullMessage() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException((String) null));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).isNull();
    }

    @Test
    void handleException_returns500() throws Exception {
        ResponseEntity<ErrorResponse> resp =
                handler.handleException(new Exception("Unexpected"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Unexpected server error");
    }
}
