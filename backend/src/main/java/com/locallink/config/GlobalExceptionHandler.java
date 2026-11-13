package com.locallink.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Global error handler.
 *
 * Ensures every error response from /api/** uses the contract defined in
 * docs/ARCHITECTURE.md: { "error": "CODE", "message": "human text" }
 *
 * Never exposes a stack trace or internal message to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Malformed JSON body — e.g. POST /api/needs/decompose with invalid JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(HttpMessageNotReadableException ex) {
        log.debug("Bad request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", "Request body is missing or malformed JSON"));
    }

    /** Type mismatch in a path variable or query param. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", "Invalid parameter: " + ex.getName()));
    }

    /** IllegalArgumentException thrown by service layer (e.g. unknown city). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }

    /**
     * Catch-all: any unexpected exception.
     * Logs the full trace server-side; returns a generic 500 to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "An unexpected error occurred"));
    }
}
