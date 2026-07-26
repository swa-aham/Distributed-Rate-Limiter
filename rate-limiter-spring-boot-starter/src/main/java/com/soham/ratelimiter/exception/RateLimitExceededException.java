package com.soham.ratelimiter.exception;

/**
 * Thrown by {@link com.soham.ratelimiter.service.RateLimiterService} when a
 * caller has exceeded the limit configured on a {@code @RateLimit} method.
 * Translated into an HTTP 429 response by {@link RateLimitExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
