package com.fm.be.common.interceptor;

import com.fm.be.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Redis-backed per-user rate limiting interceptor.
 *
 * <p>Uses Redis atomic counters with TTL windows to enforce
 * per-endpoint rate limits. Different limits apply to different
 * endpoint patterns (OTP requests, location updates, general API).</p>
 *
 * <p>Unauthenticated requests (health, auth, swagger) are skipped.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.otp-requests-per-minute:5}")
    private int otpLimit;

    @Value("${app.rate-limit.location-updates-per-10s:1}")
    private int locationLimit;

    @Value("${app.rate-limit.default-requests-per-minute:100}")
    private int defaultLimit;

    /**
     * Checks rate limits before request processing.
     *
     * @return true if under the limit, throws if exceeded
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String path = request.getRequestURI();

        if (isExcluded(path)) {
            return true;
        }

        String userId = extractUserId();
        if (userId == null) {
            return true;
        }

        RateLimitConfig config = resolveConfig(path);
        String key = RATE_LIMIT_KEY_PREFIX + config.keySuffix
                + ":" + userId;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, config.window);
        }

        if (count != null && count > config.maxRequests) {
            log.warn("Rate limit exceeded: userId={}, path={}, "
                    + "count={}, limit={}",
                    userId, path, count, config.maxRequests);
            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded: %d/%d requests in %ds",
                            count, config.maxRequests,
                            config.window.getSeconds()));
        }

        return true;
    }

    /**
     * Resolves the rate limit configuration for a given path.
     */
    RateLimitConfig resolveConfig(String path) {
        if (path.contains("/auth/otp")) {
            return new RateLimitConfig("otp", otpLimit,
                    Duration.ofMinutes(1));
        }
        if (path.contains("/location/update")) {
            return new RateLimitConfig("loc", locationLimit,
                    Duration.ofSeconds(10));
        }
        return new RateLimitConfig("api", defaultLimit,
                Duration.ofMinutes(1));
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/v1/health")
                || path.startsWith("/v1/auth")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Rate limit configuration for a specific endpoint pattern.
     */
    record RateLimitConfig(String keySuffix, int maxRequests,
                           Duration window) {
    }
}
