package com.soham.ratelimiter.strategy;

import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;

/**
 * Base class for strategy tests. Starts a lightweight in-memory Redis
 * implementation (jedis-mock) once per test class so strategy tests exercise
 * real Redis commands (INCR, EXPIRE, ZADD, Lua scripts, ...) without needing
 * a real Redis server or Docker in the test environment.
 */
abstract class RedisTestSupport {

    private static RedisServer redisServer;
    protected static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = RedisServer.newRedisServer().start();

        JedisConnectionFactory connectionFactory =
                new JedisConnectionFactory(new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                        redisServer.getHost(), redisServer.getBindPort()));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
