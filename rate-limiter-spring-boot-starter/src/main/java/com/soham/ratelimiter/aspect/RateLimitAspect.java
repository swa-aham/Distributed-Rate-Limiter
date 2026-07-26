package com.soham.ratelimiter.aspect;

import com.soham.ratelimiter.annotation.RateLimit;
import com.soham.ratelimiter.exception.RateLimitExceededException;
import com.soham.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Intercepts every method annotated with {@link RateLimit}, extracts the
 * caller identity and endpoint from the current HTTP request, and delegates
 * the actual allow/deny decision to {@link RateLimiterService}.
 * <p>
 * This class intentionally contains <b>no</b> rate limiting business logic —
 * it is purely an adapter between Spring AOP / the Servlet API and the
 * service layer, so the enforcement logic stays testable in isolation from
 * AOP machinery.
 * <p>
 * {@code @Aspect} is required for AspectJ to recognize the advice below, but
 * this class is not {@code @Component}-scanned: {@code RateLimiterAutoConfiguration}
 * registers it explicitly as a bean, which is what makes Spring's AOP
 * auto-proxy creator pick it up.
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ANONYMOUS_USER = "anonymous";

    private final RateLimiterService rateLimiterService;

    @Before("@annotation(rateLimit)")
    public void enforceRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        String userId = extractUserId();
        String endpoint = extractEndpoint(joinPoint);

        boolean allowed = rateLimiterService.isAllowed(
                userId,
                endpoint,
                rateLimit.limit(),
                rateLimit.windowSeconds(),
                rateLimit.algorithm());

        if (!allowed) {
            log.info("Rate limit exceeded for userId={} endpoint={} algorithm={}",
                    userId, endpoint, rateLimit.algorithm());
            throw new RateLimitExceededException(
                    "Rate limit exceeded for endpoint '" + endpoint + "'");
        }
    }

    private String extractUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            // Not running inside an HTTP request (e.g. called from a test or a
            // scheduled job) — fall back to a stable bucket rather than failing.
            return ANONYMOUS_USER;
        }

        HttpServletRequest request = attributes.getRequest();
        String userId = request.getHeader(USER_ID_HEADER);
        return (userId == null || userId.isBlank()) ? ANONYMOUS_USER : userId;
    }

    private String extractEndpoint(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
