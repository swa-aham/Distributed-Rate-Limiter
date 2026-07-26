package com.soham.ratelimiter.annotation;

import com.soham.ratelimiter.algorithm.Algorithm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring MVC handler method as rate limited.
 * <p>
 * Example:
 * <pre>{@code
 * @RateLimit(limit = 100, windowSeconds = 60, algorithm = Algorithm.TOKEN_BUCKET)
 * @GetMapping("/books")
 * public List<Book> getBooks() { ... }
 * }</pre>
 * <p>
 * Requests are keyed per caller using the {@code X-User-Id} header (see
 * {@link com.soham.ratelimiter.aspect.RateLimitAspect}) combined with the
 * endpoint being invoked, so limits are tracked independently per user per
 * endpoint.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests permitted within {@link #windowSeconds()}.
     */
    int limit();

    /**
     * Size of the time window, in seconds, over which {@link #limit()} applies.
     */
    int windowSeconds();

    /**
     * Which {@link com.soham.ratelimiter.strategy.RateLimitStrategy} implementation should enforce this limit.
     * Defaults to a simple fixed window counter.
     */
    Algorithm algorithm() default Algorithm.FIXED_WINDOW;
}
