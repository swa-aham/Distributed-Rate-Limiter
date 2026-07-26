package com.soham.ratelimiter.algorithm;

/**
 * Identifies the rate limiting algorithm to apply for a given {@code @RateLimit}
 * annotated method.
 * <p>
 * Adding a new algorithm requires three steps, and nothing else in the codebase
 * needs to change:
 * <ol>
 *     <li>Add a new constant here.</li>
 *     <li>Create a new class implementing {@link com.soham.ratelimiter.strategy.RateLimitStrategy}.</li>
 *     <li>Register the mapping in {@link com.soham.ratelimiter.factory.RateLimitStrategyFactory}.</li>
 * </ol>
 */
public enum Algorithm {
    FIXED_WINDOW,
    SLIDING_WINDOW,
    TOKEN_BUCKET,
    LEAKY_BUCKET
}
