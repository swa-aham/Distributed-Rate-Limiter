package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import com.soham.ratelimiter.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Sliding-window-log algorithm implemented with a Redis Sorted Set.
 * <p>
 * Each request is stored as a member in a ZSET, scored by its arrival
 * timestamp (epoch millis). On every request we:
 * <ol>
 *     <li>Remove members whose score is older than {@code now - windowSeconds}
 *     ({@code ZREMRANGEBYSCORE}) — these are outside the sliding window.</li>
 *     <li>Count the remaining members ({@code ZCARD}).</li>
 *     <li>If the count is below the limit, add the current request
 *     ({@code ZADD}) and allow it; otherwise reject it.</li>
 * </ol>
 * This gives an accurate rolling window (no boundary burst issue) at the cost
 * of storing one Redis entry per request until it ages out.
 */
@Slf4j
@RequiredArgsConstructor
public class SlidingWindowStrategy implements RateLimitStrategy {

    private static final String ALGORITHM_NAME = "sliding_window";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean allowRequest(String userId, String endpoint, RateLimitConfig config) {
        String key = RedisKeyGenerator.generateKey(ALGORITHM_NAME, userId, endpoint);

        long now = System.currentTimeMillis();
        long windowStart = now - Duration.ofSeconds(config.getWindowSeconds()).toMillis();

        // Drop anything that has aged out of the window.
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);

        Long currentCount = redisTemplate.opsForZSet().zCard(key);
        if (currentCount == null) {
            currentCount = 0L;
        }

        boolean allowed = currentCount < config.getLimit();
        if (allowed) {
            // Unique member per request so concurrent calls at the same millisecond don't collide.
            String member = now + ":" + UUID.randomUUID();
            redisTemplate.opsForZSet().add(key, member, now);
            // Keep the key from lingering forever once traffic stops.
            redisTemplate.expire(key, Duration.ofSeconds(config.getWindowSeconds()));
        }

        log.debug("SlidingWindow key={} count={} limit={} allowed={}", key, currentCount, config.getLimit(), allowed);
        return allowed;
    }
}
