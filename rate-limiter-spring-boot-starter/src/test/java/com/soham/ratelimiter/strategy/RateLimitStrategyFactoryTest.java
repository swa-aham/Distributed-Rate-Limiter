package com.soham.ratelimiter.strategy;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.factory.RateLimitStrategyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitStrategyFactoryTest {

    @Test
    void resolvesTheStrategyRegisteredForEachAlgorithm() {
        RateLimitStrategy fixedWindow = Mockito.mock(RateLimitStrategy.class);
        RateLimitStrategy tokenBucket = Mockito.mock(RateLimitStrategy.class);

        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(Map.of(
                Algorithm.FIXED_WINDOW, fixedWindow,
                Algorithm.TOKEN_BUCKET, tokenBucket
        ));

        assertThat(factory.getStrategy(Algorithm.FIXED_WINDOW)).isSameAs(fixedWindow);
        assertThat(factory.getStrategy(Algorithm.TOKEN_BUCKET)).isSameAs(tokenBucket);
    }

    @Test
    void switchingTheRequestedAlgorithmReturnsADifferentStrategyInstance() {
        RateLimitStrategy fixedWindow = Mockito.mock(RateLimitStrategy.class);
        RateLimitStrategy slidingWindow = Mockito.mock(RateLimitStrategy.class);

        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(Map.of(
                Algorithm.FIXED_WINDOW, fixedWindow,
                Algorithm.SLIDING_WINDOW, slidingWindow
        ));

        assertThat(factory.getStrategy(Algorithm.FIXED_WINDOW))
                .isNotSameAs(factory.getStrategy(Algorithm.SLIDING_WINDOW));
    }

    @Test
    void throwsAClearErrorWhenNoStrategyIsRegisteredForAnAlgorithm() {
        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(Map.of());

        assertThatThrownBy(() -> factory.getStrategy(Algorithm.LEAKY_BUCKET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEAKY_BUCKET");
    }
}
