package com.soham.ratelimiter.util;

/**
 * Centralizes the Redis key naming convention so every strategy addresses
 * Redis the same way: {@code rate_limit:<algorithm>:<userId>:<endpoint>}.
 * <p>
 * Including the algorithm name in the key means switching a method's
 * {@code algorithm} attribute never collides with counters left behind by a
 * previously configured algorithm.
 */
public final class RedisKeyGenerator {

    private static final String PREFIX = "rate_limit";

    private RedisKeyGenerator() {
    }

    public static String generateKey(String algorithmName, String userId, String endpoint) {
        return PREFIX + ":" + algorithmName + ":" + userId + ":" + endpoint;
    }
}
