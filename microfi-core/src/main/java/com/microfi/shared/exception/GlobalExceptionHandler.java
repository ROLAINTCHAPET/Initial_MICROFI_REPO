package com.microfi.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring's default WebFlux error body omits "message" unless explicitly configured, and that
 * config (server.error.include-message) wasn't taking effect on this Spring Boot version — so
 * every {@link ResponseStatusException} reason and {@code @Valid} failure message we deliberately
 * wrote as user-facing text was silently dropped; callers only ever saw the generic HTTP reason
 * phrase ("Bad Request", "Conflict"). This builds the response body ourselves instead of relying
 * on framework defaults, so it can't regress the same way again. Never includes a stack trace or
 * exception class name — only the deliberate, human-written reason string.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return body(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(), exchange);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex, ServerWebExchange exchange) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), exchange);
    }

    /** @Valid failures on a @RequestBody — joins each field's own message instead of one generic sentence. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        String message = ex.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        return body(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleInput(ServerWebInputException ex, ServerWebExchange exchange) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request body", exchange);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
