package com.example.placement.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mongodb.MongoException;

/**
 * Extends {@link ResponseEntityExceptionHandler} so that the standard Spring MVC
 * exceptions keep their correct status codes. Without it, the catch-all
 * {@code Exception} handler below would win over Spring's own resolver and turn
 * every 400/405/415 into a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatusException(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason() : "Request failed";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }

    /**
     * Replaces the inherited handler so the response keeps the existing
     * {@code {"message", "errors"}} shape used by the frontend.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Validation failed");
        body.put("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(java.util.stream.Collectors.toList()));
        return new ResponseEntity<>(body, headers, HttpStatus.BAD_REQUEST);
    }

    public ResponseEntity<Map<String, Object>> handleMissingResource(NoResourceFoundException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({MongoException.class, DataAccessException.class})
    public ResponseEntity<Map<String, Object>> handleMongoOutage(Exception exception) {
        log.error("MongoDB request failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "Database temporarily unavailable. Check MongoDB network and try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception exception) {
        log.error("Unhandled exception", exception);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Internal server error");
        return ResponseEntity.internalServerError().body(body);
    }

    /**
     * Emits the same {@code {"message": ...}} envelope for every exception handled
     * by the parent class, instead of the default RFC 7807 problem document.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        boolean alreadyEnveloped = body instanceof Map<?, ?> map && map.containsKey("message");
        if (!alreadyEnveloped) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", status.is4xxClientError() ? "Invalid request" : "Request failed");
            body = payload;
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}
