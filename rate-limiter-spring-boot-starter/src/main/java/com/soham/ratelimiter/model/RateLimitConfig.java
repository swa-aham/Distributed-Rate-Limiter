package com.soham.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable snapshot of the values declared on a {@code @RateLimit} annotation
 * for a single invocation. Strategies receive this instead of the annotation
 * itself so that they stay decoupled from Spring AOP / reflection concerns.
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class RateLimitConfig {

    /** Maximum number of allowed requests within {@link #windowSeconds}. */
    private final int limit;

    /** Length of the rate limiting window, in seconds. */
    private final int windowSeconds;
}
