package com.booknest.wishlistservice.exception;

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
    void handleResponseStatus_mapsStatusAndReason() {
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");

        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().errorCode()).isEqualTo("REQUEST_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Access denied");
    }

    @Test
    void handleResponseStatus_nullReason_fallsBackToMessage() {
        // When reason is null, falls back to ex.getMessage()
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.NOT_FOUND);

        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().message()).isNotNull();
    }

    @Test
    void handleValidation_returns400WithFieldMessage() {
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(
                List.of(new FieldError("obj", "userId", "must not be null")));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(br);

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().message()).contains("userId");
    }

    @Test
    void handleRuntime_returns500() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException("Wishlist not found"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("BUSINESS_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Wishlist not found");
    }

    @Test
    void handleUnhandled_returns500WithGenericMessage() throws Exception {
        ResponseEntity<ErrorResponse> resp =
                handler.handleUnhandled(new Exception("Something broke"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Unexpected server error");
    }
}
