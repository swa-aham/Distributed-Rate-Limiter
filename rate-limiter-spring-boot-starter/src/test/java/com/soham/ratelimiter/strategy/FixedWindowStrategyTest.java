package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowStrategyTest extends RedisTestSupport {

    @Test
    void allowsRequestsUpToTheLimit() {
        FixedWindowStrategy strategy = new FixedWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(3).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
    }

    @Test
    void rejectsRequestsOnceLimitIsExceeded() {
        FixedWindowStrategy strategy = new FixedWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(2).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-2", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-2", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-2", "GET /books", config)).isFalse();
    }

    @Test
    void tracksDifferentUsersIndependently() {
        FixedWindowStrategy strategy = new FixedWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(1).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-b", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-a", "GET /books", config)).isFalse();
        assertThat(strategy.allowRequest("user-b", "GET /books", config)).isFalse();
    }

    @Test
    void tracksDifferentEndpointsIndependently() {
        FixedWindowStrategy strategy = new FixedWindowStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(1).windowSeconds(60).build();

        assertThat(strategy.allowRequest("user-3", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-3", "GET /authors", config)).isTrue();
    }
}
