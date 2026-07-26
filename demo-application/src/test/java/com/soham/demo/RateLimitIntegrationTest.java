package com.soham.demo;

import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end proof that the whole chain works without the demo application
 * declaring a single rate-limiter bean itself:
 * <p>
 * {@code @RateLimit} annotation -&gt; {@code RateLimitAspect} intercepts the
 * call -&gt; {@code RateLimiterService} + strategy check Redis -&gt; allowed
 * requests pass through, the request that exceeds the limit gets HTTP 429
 * with the documented JSON body.
 * <p>
 * Redis itself is an embedded jedis-mock instance so this test has no
 * external Docker/Redis dependency; running against real Redis (via
 * {@code docker-compose up}) behaves identically.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static RedisServer redisServer;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startEmbeddedRedis() throws IOException {
        redisServer = RedisServer.newRedisServer().start();
    }

    @AfterAll
    static void stopEmbeddedRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redisServer.getHost());
        registry.add("spring.data.redis.port", () -> redisServer.getBindPort());
    }

    @Test
    void allowsRequestsWithinTheFixedWindowLimitThenRejectsWithHttp429() throws Exception {
        String user = "X-User-Id";
        String userId = "integration-user-1";

        // BookController#getBooks is configured with limit = 5, windowSeconds = 60.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/books").header(user, userId))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/books").header(user, userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }

    @Test
    void differentUsersGetIndependentLimits() throws Exception {
        String user = "X-User-Id";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/books").header(user, "integration-user-2"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/books").header(user, "integration-user-2"))
                .andExpect(status().isTooManyRequests());

        // A different user hitting the same endpoint is unaffected.
        mockMvc.perform(get("/books").header(user, "integration-user-3"))
                .andExpect(status().isOk());
    }

    @Test
    void switchingToTokenBucketAlgorithmOnAnotherEndpointAlsoEnforcesItsOwnLimit() throws Exception {
        String user = "X-User-Id";
        String userId = "integration-user-4";

        // BookController#getBooksBurstable is configured with limit = 3, windowSeconds = 30,
        // algorithm = TOKEN_BUCKET - proves the algorithm switch takes effect per endpoint.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/books/burstable").header(user, userId))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/books/burstable").header(user, userId))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"));
    }
}
