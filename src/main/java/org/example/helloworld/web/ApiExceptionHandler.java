package org.example.helloworld.web;

import org.example.helloworld.greeting.InvalidNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps validation failures to {@code 400 {"error": "Invalid Input"}}. The detailed reason
 * is logged but not returned to the client. Other errors (404, 405, 406) keep Spring's default
 * handling.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidNameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidName(InvalidNameException ex) {
        // Logged at INFO without a stack trace: a 400 is a normal client mistake, not a server
        // fault. The reason never contains the submitted name (personal data).
        log.info("Rejected request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.INVALID_INPUT);
    }
}
