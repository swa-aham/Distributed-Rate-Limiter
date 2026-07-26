package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import com.soham.ratelimiter.util.RedisKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * Leaky bucket algorithm (queue/meter variant).
 * <p>
 * Each {@code (userId, endpoint)} pair has a virtual queue whose current
 * fill level is stored in Redis as a hash with two fields:
 * <ul>
 *     <li>{@code level} — current queue occupancy</li>
 *     <li>{@code lastLeak} — epoch millis of the last leak calculation</li>
 * </ul>
 * The queue "leaks" (drains) at a constant rate of
 * {@code limit / windowSeconds} requests per second — this is the maximum
 * sustained processing rate. On every call the level is first drained
 * according to elapsed time; if there is still room for one more request
 * under {@code capacity} ({@code = limit}), the request is admitted and the
 * level is incremented by one, otherwise it is rejected as if it overflowed
 * the queue.
 * <p>
 * Unlike {@link TokenBucketStrategy}, which allows saved-up capacity to be
 * spent in a burst, leaky bucket smooths traffic out to a strictly constant
 * outflow rate — bursts beyond capacity overflow and are dropped rather than
 * queued indefinitely.
 * <p>
 * The read-leak-increment-write sequence runs as a single Lua script so it
 * stays atomic across concurrent requests from multiple application
 * instances sharing the same Redis.
 */
@Slf4j
public class LeakyBucketStrategy implements RateLimitStrategy {

    private static final String ALGORITHM_NAME = "leaky_bucket";

    // KEYS[1] = bucket key
    // ARGV[1] = capacity (limit)
    // ARGV[2] = leak rate: requests drained per second (limit / windowSeconds)
    // ARGV[3] = current time in millis
    // ARGV[4] = window seconds (used for the key TTL, so idle buckets expire)
    //
    // Returns 1 if the request is admitted into the queue, 0 if it overflows.
    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leakRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'level', 'lastLeak')
            local level = tonumber(bucket[1])
            local lastLeak = tonumber(bucket[2])

            if level == nil then
                level = 0
                lastLeak = now
            end

            local elapsedSeconds = math.max(0, (now - lastLeak) / 1000)
            local leaked = math.max(0, level - (elapsedSeconds * leakRate))

            local allowed = 0
            if leaked + 1 <= capacity then
                leaked = leaked + 1
                allowed = 1
            end

            redis.call('HMSET', key, 'level', leaked, 'lastLeak', now)
            redis.call('EXPIRE', key, ttl)

            return allowed
            """;

    private static final RedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public LeakyBucketStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allowRequest(String userId, String endpoint, RateLimitConfig config) {
        String key = RedisKeyGenerator.generateKey(ALGORITHM_NAME, userId, endpoint);

        double leakRate = config.getLimit() / (double) config.getWindowSeconds();
        long now = System.currentTimeMillis();
        long ttlSeconds = Math.max(1, config.getWindowSeconds() * 2L);

        List<String> keys = Collections.singletonList(key);
        Long result = redisTemplate.execute(
                REDIS_SCRIPT,
                keys,
                String.valueOf(config.getLimit()),
                String.valueOf(leakRate),
                String.valueOf(now),
                String.valueOf(ttlSeconds)
        );

        boolean allowed = result != null && result == 1L;
        log.debug("LeakyBucket key={} capacity={} leakRate={}/s allowed={}", key, config.getLimit(), leakRate, allowed);
        return allowed;
    }
}
