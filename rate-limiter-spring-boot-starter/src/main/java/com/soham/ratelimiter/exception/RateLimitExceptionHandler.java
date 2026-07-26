package com.soham.ratelimiter.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Converts a {@link RateLimitExceededException} raised anywhere in a consuming
 * application into a uniform HTTP 429 (Too Many Requests) JSON response:
 * <pre>{@code {"message": "Rate limit exceeded"}}</pre>
 * <p>
 * Because this is packaged inside the starter and picked up by Spring Boot's
 * component scanning / autoconfiguration, consuming applications get this
 * behavior automatically without declaring their own {@code @ControllerAdvice}.
 */
@Slf4j
@RestControllerAdvice
public class RateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.info("Rejecting request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("message", "Rate limit exceeded"));
    }
}
