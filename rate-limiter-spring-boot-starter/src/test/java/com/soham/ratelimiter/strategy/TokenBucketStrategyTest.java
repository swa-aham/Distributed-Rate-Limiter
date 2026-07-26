package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketStrategyTest extends RedisTestSupport {

    @Test
    void allowsBurstUpToBucketCapacityThenRejects() {
        TokenBucketStrategy strategy = new TokenBucketStrategy(redisTemplate);
        // Capacity = 3 tokens, refilling slowly (1 token per 60s) so the burst
        // exhausts the bucket without any refill kicking in during the test.
        RateLimitConfig config = RateLimitConfig.builder().limit(3).windowSeconds(180).build();

        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isFalse();
    }

    @Test
    void tracksDifferentUsersIndependently() {
        TokenBucketStrategy strategy = new TokenBucketStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(1).windowSeconds(120).build();

        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-b", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isFalse();
    }
}
