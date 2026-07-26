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
 * Token bucket algorithm.
 * <p>
 * Each {@code (userId, endpoint)} pair owns a bucket, represented in Redis as
 * a hash with two fields:
 * <ul>
 *     <li>{@code tokens} — tokens currently available</li>
 *     <li>{@code lastRefill} — epoch millis of the last refill calculation</li>
 * </ul>
 * The bucket capacity is {@code config.getLimit()} and it refills at a
 * constant rate of {@code limit / windowSeconds} tokens per second, so over
 * any {@code windowSeconds} period at most {@code limit} requests are allowed,
 * but unlike fixed/sliding window, unused capacity can burst: a caller that
 * has been idle can spend its whole bucket at once.
 * <p>
 * The read-refill-decrement-write sequence is executed as a single Lua script
 * so it is atomic even under concurrent requests from multiple app instances —
 * a plain Java read-then-write would have a race condition between instances.
 */
@Slf4j
public class TokenBucketStrategy implements RateLimitStrategy {

    private static final String ALGORITHM_NAME = "token_bucket";

    // KEYS[1] = bucket key
    // ARGV[1] = capacity (limit)
    // ARGV[2] = refill rate: tokens added per second (limit / windowSeconds)
    // ARGV[3] = current time in millis
    // ARGV[4] = window seconds (used for the key TTL, so idle buckets expire)
    //
    // Returns 1 if the request is allowed (a token was consumed), 0 otherwise.
    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
            local tokens = tonumber(bucket[1])
            local lastRefill = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                lastRefill = now
            end

            local elapsedSeconds = math.max(0, (now - lastRefill) / 1000)
            local refilled = math.min(capacity, tokens + (elapsedSeconds * refillRate))

            local allowed = 0
            if refilled >= 1 then
                refilled = refilled - 1
                allowed = 1
            end

            redis.call('HMSET', key, 'tokens', refilled, 'lastRefill', now)
            redis.call('EXPIRE', key, ttl)

            return allowed
            """;

    private static final RedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>(SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;

    public TokenBucketStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allowRequest(String userId, String endpoint, RateLimitConfig config) {
        String key = RedisKeyGenerator.generateKey(ALGORITHM_NAME, userId, endpoint);

        double refillRate = config.getLimit() / (double) config.getWindowSeconds();
        long now = System.currentTimeMillis();

        // TTL is generous (2x window) so an idle bucket doesn't linger forever,
        // while still surviving comfortably between bursts within the window.
        long ttlSeconds = Math.max(1, config.getWindowSeconds() * 2L);

        List<String> keys = Collections.singletonList(key);
        Long result = redisTemplate.execute(
                REDIS_SCRIPT,
                keys,
                String.valueOf(config.getLimit()),
                String.valueOf(refillRate),
                String.valueOf(now),
                String.valueOf(ttlSeconds)
        );

        boolean allowed = result != null && result == 1L;
        log.debug("TokenBucket key={} limit={} refillRate={}/s allowed={}", key, config.getLimit(), refillRate, allowed);
        return allowed;
    }
}
