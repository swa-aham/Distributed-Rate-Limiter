package com.soham.ratelimiter.factory;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.strategy.RateLimitStrategy;

import java.util.Map;
import java.util.Objects;

/**
 * Resolves the {@link RateLimitStrategy} implementation for a given
 * {@link Algorithm}.
 * <p>
 * Deliberately implemented with a {@code Map<Algorithm, RateLimitStrategy>}
 * instead of an if/else or switch chain, so that:
 * <ul>
 *     <li>The factory's code never changes when a new algorithm is added.</li>
 *     <li>All wiring lives in one place — {@code RateLimiterAutoConfiguration} —
 *     which builds the map and hands it to this factory.</li>
 * </ul>
 * To add a new algorithm: add an {@link Algorithm} constant, implement
 * {@link RateLimitStrategy}, and add one entry to the map passed in here.
 */
public class RateLimitStrategyFactory {

    private final Map<Algorithm, RateLimitStrategy> strategies;

    public RateLimitStrategyFactory(Map<Algorithm, RateLimitStrategy> strategies) {
        this.strategies = Map.copyOf(strategies);
    }

    public RateLimitStrategy getStrategy(Algorithm algorithm) {
        RateLimitStrategy strategy = strategies.get(Objects.requireNonNull(algorithm, "algorithm must not be null"));
        if (strategy == null) {
            throw new IllegalStateException(
                    "No RateLimitStrategy registered for algorithm '" + algorithm +
                    "'. Register one in RateLimiterAutoConfiguration.");
        }
        return strategy;
    }
}
