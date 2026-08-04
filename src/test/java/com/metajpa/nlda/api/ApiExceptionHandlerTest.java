package com.metajpa.nlda.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsSafeValidationResponse() {
        ResponseEntity<QueryResponse> response = handler.handleValidationFailure();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("REJECTED");
        assertThat(response.getBody().reason()).contains("Question is required");
        assertThat(response.getBody().sql()).isNull();
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void returnsSafeUnexpectedFailureResponse() {
        ResponseEntity<QueryResponse> response = handler.handleUnexpectedFailure(new RuntimeException("database password"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("REJECTED");
        assertThat(response.getBody().reason()).doesNotContain("Exception");
    }

    @Test
    void returnsBadRequestForMalformedJson() {
        ResponseEntity<QueryResponse> response = handler.handleMalformedJson();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reason()).contains("JSON");
        assertThat(response.getBody().sql()).isNull();
    }

    @Test
    void returnsUnsupportedMediaTypeForNonJsonRequest() {
        ResponseEntity<QueryResponse> response = handler.handleUnsupportedMediaType();

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().reason()).contains("application/json");
        assertThat(response.getBody().sql()).isNull();
    }
}
