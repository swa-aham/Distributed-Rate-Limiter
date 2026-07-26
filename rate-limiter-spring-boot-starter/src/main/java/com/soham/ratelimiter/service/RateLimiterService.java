package com.soham.ratelimiter.service;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.factory.RateLimitStrategyFactory;
import com.soham.ratelimiter.model.RateLimitConfig;
import com.soham.ratelimiter.strategy.RateLimitStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Central entry point for rate limit decisions.
 * <p>
 * This is the only place that knows how to go from "here's a request and its
 * configured limit" to "allowed or not" — {@link com.soham.ratelimiter.aspect.RateLimitAspect}
 * stays a thin adapter that only deals with AOP/reflection concerns and
 * delegates all business logic here, keeping the two responsibilities
 * separate (Single Responsibility Principle).
 * <p>
 * Instantiated explicitly as a {@code @Bean} in {@code RateLimiterAutoConfiguration}
 * rather than annotated {@code @Service} — this package is not on a consuming
 * application's component-scan path, so autoconfiguration is the single
 * source of truth for how this starter's beans get created.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimiterService {

    private final RateLimitStrategyFactory strategyFactory;

    /**
     * @return {@code true} if the request is allowed, {@code false} if it
     *         should be rejected with HTTP 429.
     */
    public boolean isAllowed(String userId, String endpoint, int limit, int windowSeconds, Algorithm algorithm) {
        RateLimitStrategy strategy = strategyFactory.getStrategy(algorithm);
        RateLimitConfig config = RateLimitConfig.builder()
                .limit(limit)
                .windowSeconds(windowSeconds)
                .build();

        boolean allowed = strategy.allowRequest(userId, endpoint, config);
        log.debug("RateLimiterService userId={} endpoint={} algorithm={} allowed={}",
                userId, endpoint, algorithm, allowed);
        return allowed;
    }
}
