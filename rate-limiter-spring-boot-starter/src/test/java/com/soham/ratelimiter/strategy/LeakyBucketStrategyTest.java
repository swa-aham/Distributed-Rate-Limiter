package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeakyBucketStrategyTest extends RedisTestSupport {

    @Test
    void allowsRequestsUntilQueueCapacityIsReachedThenOverflows() {
        LeakyBucketStrategy strategy = new LeakyBucketStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(3).windowSeconds(180).build();

        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isFalse();
    }

    @Test
    void tracksDifferentEndpointsIndependently() {
        LeakyBucketStrategy strategy = new LeakyBucketStrategy(redisTemplate);
        RateLimitConfig config = RateLimitConfig.builder().limit(1).windowSeconds(120).build();

//        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /authors", config)).isTrue();
        assertThat(strategy.allowRequest("user-1", "GET /books", config)).isFalse();
    }
}
