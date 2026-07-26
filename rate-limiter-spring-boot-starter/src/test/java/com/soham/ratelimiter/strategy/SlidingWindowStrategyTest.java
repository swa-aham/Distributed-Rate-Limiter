package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowStrategyTest extends RedisTestSupport {

    @Test
    void allowsRequestsUpToTheLimitWithinTheWindow() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(3).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isFalse();
    }

    @Test
    void tracksDifferentUsersIndependently() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(1).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-b", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isFalse();
    }
}
