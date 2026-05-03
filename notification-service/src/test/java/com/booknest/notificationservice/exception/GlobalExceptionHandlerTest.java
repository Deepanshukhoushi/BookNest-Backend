package com.booknest.notificationservice.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCESS_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Access denied");
    }

    @Test
    void handleRuntime_returns500() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleRuntime(new RuntimeException("Something failed"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("BUSINESS_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Something failed");
    }

    @Test
    void handleUnhandled_returns500WithGenericMessage() throws Exception {
        ResponseEntity<ErrorResponse> resp =
                handler.handleUnhandled(new Exception("Unknown failure"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().message()).isEqualTo("Unexpected server error");
    }
}
