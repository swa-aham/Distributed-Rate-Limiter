package com.soham.ratelimiter.config;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.aspect.RateLimitAspect;
import com.soham.ratelimiter.exception.RateLimitExceptionHandler;
import com.soham.ratelimiter.factory.RateLimitStrategyFactory;
import com.soham.ratelimiter.service.RateLimiterService;
import com.soham.ratelimiter.strategy.FixedWindowStrategy;
import com.soham.ratelimiter.strategy.LeakyBucketStrategy;
import com.soham.ratelimiter.strategy.RateLimitStrategy;
import com.soham.ratelimiter.strategy.SlidingWindowStrategy;
import com.soham.ratelimiter.strategy.TokenBucketStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.EnumMap;
import java.util.Map;

/**
 * Auto-configures every bean the rate limiter needs the moment this starter
 * is on the classpath — consuming applications never declare any of these
 * beans themselves.
 * <p>
 * Ordered after {@link RedisAutoConfiguration} so the {@link StringRedisTemplate}
 * bean Spring Boot creates from {@code spring.data.redis.*} properties is
 * already available for injection here.
 * <p>
 * Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * (the Spring Boot 3+ replacement for {@code spring.factories}).
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class RateLimiterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FixedWindowStrategy fixedWindowStrategy(StringRedisTemplate redisTemplate) {
        return new FixedWindowStrategy(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowStrategy slidingWindowStrategy(StringRedisTemplate redisTemplate) {
        return new SlidingWindowStrategy(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenBucketStrategy tokenBucketStrategy(StringRedisTemplate redisTemplate) {
        return new TokenBucketStrategy(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public LeakyBucketStrategy leakyBucketStrategy(StringRedisTemplate redisTemplate) {
        return new LeakyBucketStrategy(redisTemplate);
    }

    /**
     * Builds the {@code Algorithm -> RateLimitStrategy} map handed to the
     * factory. This is the single place that wires new algorithms together —
     * adding one is a one-line addition here, nothing else in this class
     * changes.
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitStrategyFactory rateLimitStrategyFactory(
            FixedWindowStrategy fixedWindowStrategy,
            SlidingWindowStrategy slidingWindowStrategy,
            TokenBucketStrategy tokenBucketStrategy,
            LeakyBucketStrategy leakyBucketStrategy) {

        Map<Algorithm, RateLimitStrategy> strategies = new EnumMap<>(Algorithm.class);
        strategies.put(Algorithm.FIXED_WINDOW, fixedWindowStrategy);
        strategies.put(Algorithm.SLIDING_WINDOW, slidingWindowStrategy);
        strategies.put(Algorithm.TOKEN_BUCKET, tokenBucketStrategy);
        strategies.put(Algorithm.LEAKY_BUCKET, leakyBucketStrategy);

        return new RateLimitStrategyFactory(strategies);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimiterService rateLimiterService(RateLimitStrategyFactory rateLimitStrategyFactory) {
        return new RateLimiterService(rateLimitStrategyFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiterService rateLimiterService) {
        return new RateLimitAspect(rateLimiterService);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitExceptionHandler rateLimitExceptionHandler() {
        return new RateLimitExceptionHandler();
    }
}
