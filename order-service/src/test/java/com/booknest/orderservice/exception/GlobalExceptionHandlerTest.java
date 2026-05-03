package com.booknest.orderservice.exception;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
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
        ResponseEntity<ErrorResponse> response =
                handler.handleInsufficientBalance(new InsufficientBalanceException("No funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(response.getBody().getMessage()).isEqualTo("No funds");
    }

    @Test
    void handleInsufficientStock_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInsufficientStock(new InsufficientStockException("Out of stock"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void handleInvalidPayment_returns400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidPayment(new InvalidPaymentException("Bad payment"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_PAYMENT");
        assertThat(response.getBody().getMessage()).isEqualTo("Bad payment");
    }

    @Test
    void handleValidationException_returns400WithFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "userId", "must not be null");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("userId");
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NoSuchElementException("Not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void handleNotFound_nullMessage_usesDefault() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NoSuchElementException((String) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
    }

    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad arg"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("BUSINESS_ERROR");
    }

    @Test
    void handleIllegalState_returns409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalState(new IllegalStateException("Invalid state"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_STATE");
    }

    @Test
    void handleFeignException_5xx_returnsBadGateway() {
        Request req = Request.create(Request.HttpMethod.GET, "/url",
                Map.of(), null, new RequestTemplate());
        FeignException ex = FeignException.errorStatus("test",
                feign.Response.builder()
                        .status(503)
                        .reason("Service Unavailable")
                        .request(req)
                        .headers(Map.of())
                        .build());

        ResponseEntity<ErrorResponse> response = handler.handleFeignException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
    }

    @Test
    void handleFeignException_4xx_returnsResolvedStatus() {
        Request req = Request.create(Request.HttpMethod.POST, "/url",
                Map.of(), null, new RequestTemplate());
        FeignException ex = FeignException.errorStatus("test",
                feign.Response.builder()
                        .status(404)
                        .reason("Not Found")
                        .request(req)
                        .headers(Map.of())
                        .build());

        ResponseEntity<ErrorResponse> response = handler.handleFeignException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
    }

    @Test
    void handleResponseStatusException_returnsMappedStatus() {
        ResponseStatusException ex =
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getErrorCode()).isEqualTo("ACCESS_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void handleRuntimeException_returns500() {
        ResponseEntity<ErrorResponse> response =
                handler.handleRuntimeException(new RuntimeException("Crash!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("Crash!");
    }

    @Test
    void handleRuntimeException_nullMessage_usesDefaultMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleRuntimeException(new RuntimeException((String) null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
    }

    @Test
    void handleUnhandledException_returns500() throws Exception {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnhandledException(new Exception("Unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
    }
}
