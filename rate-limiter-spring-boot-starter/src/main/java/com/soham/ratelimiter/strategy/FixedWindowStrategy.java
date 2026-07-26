package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import com.soham.ratelimiter.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Classic fixed-window counter algorithm.
 * <p>
 * Behaviour:
 * <ol>
 *     <li>{@code INCR} the counter key for {@code (userId, endpoint)}.</li>
 *     <li>If this increment created the key (i.e. the new value is {@code 1}),
 *     attach a TTL equal to the configured window so the counter resets
 *     automatically at the window boundary.</li>
 *     <li>Allow the request only if the counter is still within {@code limit}.</li>
 * </ol>
 * Simple and cheap, but it can allow up to {@code 2x limit} requests near a
 * window boundary (bursty). {@link SlidingWindowStrategy} avoids that at the
 * cost of a bit more Redis work.
 */
@Slf4j
@RequiredArgsConstructor
public class FixedWindowStrategy implements RateLimitStrategy {

    private static final String ALGORITHM_NAME = "fixed_window";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean allowRequest(String userId, String endpoint, RateLimitConfig config) {
        String key = RedisKeyGenerator.generateKey(ALGORITHM_NAME, userId, endpoint);

        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount == null) {
            // Should not happen with a healthy Redis connection; fail safe by rejecting.
            log.warn("Redis returned null for INCR on key {}", key);
            return false;
        }

        if (currentCount == 1L) {
            // First request in this window — start the TTL clock.
            redisTemplate.expire(key, Duration.ofSeconds(config.getWindowSeconds()));
        }

        boolean allowed = currentCount <= config.getLimit();
        log.debug("FixedWindow key={} count={} limit={} allowed={}", key, currentCount, config.getLimit(), allowed);
        return allowed;
    }
}
