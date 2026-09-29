package com.example.placement.config;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mongodb.MongoException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingStaticResourceReturnsNotFoundInsteadOfServerError() {
        NoResourceFoundException exception =
                new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");

        ResponseEntity<Map<String, Object>> response = handler.handleMissingResource(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Resource not found");
    }

    @Test
    void mongoFailureReturnsUnavailableWithoutDriverDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleMongoOutage(
                new MongoException("internal driver details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("message",
                "Database temporarily unavailable. Check MongoDB network and try again.");
        assertThat(response.getBody().toString()).doesNotContain("internal driver details");
    }
}