package com.soham.ratelimiter.aspect;

import com.soham.ratelimiter.algorithm.Algorithm;
import com.soham.ratelimiter.annotation.RateLimit;
import com.soham.ratelimiter.exception.RateLimitExceededException;
import com.soham.ratelimiter.service.RateLimiterService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAspectTest {

    // Stand-in "controller method" carrying a real @RateLimit annotation,
    // used to build a genuine RateLimit instance for the aspect to read.
    static class SampleController {
        @RateLimit(limit = 5, windowSeconds = 60, algorithm = Algorithm.TOKEN_BUCKET)
        public void getBooks() {
        }
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private JoinPoint mockJoinPoint() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("getBooks");
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        JoinPoint joinPoint = Mockito.mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    @Test
    void allowsTheCallThroughWhenServiceApprovesIt() throws NoSuchMethodException {
        RateLimiterService service = Mockito.mock(RateLimiterService.class);
        when(service.isAllowed(anyString(), anyString(), anyInt(), anyInt(), any(Algorithm.class)))
                .thenReturn(true);

        RateLimitAspect aspect = new RateLimitAspect(service);
        RateLimit rateLimit = SampleController.class.getMethod("getBooks").getAnnotation(RateLimit.class);

        assertThatCode(() -> aspect.enforceRateLimit(mockJoinPoint(), rateLimit)).doesNotThrowAnyException();
    }

    @Test
    void throwsRateLimitExceededWhenServiceRejectsTheCall() throws NoSuchMethodException {
        RateLimiterService service = Mockito.mock(RateLimiterService.class);
        when(service.isAllowed(anyString(), anyString(), anyInt(), anyInt(), any(Algorithm.class)))
                .thenReturn(false);

        RateLimitAspect aspect = new RateLimitAspect(service);
        RateLimit rateLimit = SampleController.class.getMethod("getBooks").getAnnotation(RateLimit.class);

        assertThatThrownBy(() -> aspect.enforceRateLimit(mockJoinPoint(), rateLimit))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void extractsUserIdFromTheXUserIdHeader() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "soham");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RateLimiterService service = Mockito.mock(RateLimiterService.class);
        when(service.isAllowed(anyString(), anyString(), anyInt(), anyInt(), any(Algorithm.class)))
                .thenReturn(true);

        RateLimitAspect aspect = new RateLimitAspect(service);
        RateLimit rateLimit = SampleController.class.getMethod("getBooks").getAnnotation(RateLimit.class);

        aspect.enforceRateLimit(mockJoinPoint(), rateLimit);

        verify(service).isAllowed(eq("soham"), anyString(), eq(5), eq(60), eq(Algorithm.TOKEN_BUCKET));
    }

    @Test
    void fallsBackToAnonymousWhenHeaderIsMissing() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RateLimiterService service = Mockito.mock(RateLimiterService.class);
        when(service.isAllowed(anyString(), anyString(), anyInt(), anyInt(), any(Algorithm.class)))
                .thenReturn(true);

        RateLimitAspect aspect = new RateLimitAspect(service);
        RateLimit rateLimit = SampleController.class.getMethod("getBooks").getAnnotation(RateLimit.class);

        aspect.enforceRateLimit(mockJoinPoint(), rateLimit);

        verify(service).isAllowed(eq("anonymous"), anyString(), eq(5), eq(60), eq(Algorithm.TOKEN_BUCKET));
    }
}
