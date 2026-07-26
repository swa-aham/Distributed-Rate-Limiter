package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;

/**
 * Strategy Pattern contract implemented by every supported rate limiting
 * algorithm (fixed window, sliding window, token bucket, leaky bucket, ...).
 * <p>
 * Implementations must be:
 * <ul>
 *     <li><b>Stateless in the JVM</b> — all mutable state lives in Redis so that
 *     multiple instances of the same application enforce a single, shared
 *     limit (distributed rate limiting).</li>
 *     <li><b>Thread-safe</b> — a single Spring bean instance serves all
 *     concurrent requests.</li>
 * </ul>
 * New algorithms are added by creating a new implementation of this interface
 * and registering it in {@link com.soham.ratelimiter.factory.RateLimitStrategyFactory};
 * no existing strategy class ever needs to change (Open/Closed Principle).
 */
public interface RateLimitStrategy {

    /**
     * Decides whether a request from {@code userId} against {@code endpoint}
     * should be allowed, given {@code config}.
     *
     * @param userId   caller identity, extracted from the {@code X-User-Id} header
     * @param endpoint logical endpoint identifier (e.g. HTTP method + path)
     * @param config   the limit/window declared on the {@code @RateLimit} annotation
     * @return {@code true} if the request is within limits and should proceed,
     *         {@code false} if it should be rejected with HTTP 429
     */
    boolean allowRequest(String userId, String endpoint, RateLimitConfig config);
}
