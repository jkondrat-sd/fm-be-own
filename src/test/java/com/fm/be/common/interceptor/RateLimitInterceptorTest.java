package com.fm.be.common.interceptor;

import com.fm.be.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitInterceptor}.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redisTemplate);
        ReflectionTestUtils.setField(interceptor, "otpLimit", 5);
        ReflectionTestUtils.setField(interceptor, "locationLimit", 1);
        ReflectionTestUtils.setField(interceptor, "defaultLimit", 100);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("preHandle")
    class PreHandleTests {

        @Test
        @DisplayName("should allow excluded paths without rate limit check")
        void preHandle_healthEndpoint_allowed() {
            request.setRequestURI("/v1/health/status");

            boolean result = interceptor.preHandle(request, response, null);

            assertThat(result).isTrue();
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("should allow auth endpoints without rate limit check")
        void preHandle_authEndpoint_allowed() {
            request.setRequestURI("/v1/auth/login");

            boolean result = interceptor.preHandle(request, response, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow unauthenticated requests")
        void preHandle_unauthenticated_allowed() {
            request.setRequestURI("/v1/employees");

            boolean result = interceptor.preHandle(request, response, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should allow request under the limit")
        void preHandle_underLimit_allowed() {
            setupAuth("user-123");
            request.setRequestURI("/v1/employees");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("rl:api:user-123"))
                    .thenReturn(5L);

            boolean result = interceptor.preHandle(request, response, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should set TTL on first request")
        void preHandle_firstRequest_setsTtl() {
            setupAuth("user-456");
            request.setRequestURI("/v1/sites");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("rl:api:user-456"))
                    .thenReturn(1L);

            interceptor.preHandle(request, response, null);

            verify(redisTemplate).expire(
                    eq("rl:api:user-456"), eq(Duration.ofMinutes(1)));
        }

        @Test
        @DisplayName("should throw when rate limit exceeded")
        void preHandle_overLimit_throws() {
            setupAuth("user-789");
            request.setRequestURI("/v1/employees");

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("rl:api:user-789"))
                    .thenReturn(101L);

            assertThatThrownBy(() ->
                    interceptor.preHandle(request, response, null))
                    .isInstanceOf(RateLimitExceededException.class);
        }

        @Test
        @DisplayName("should allow swagger endpoints")
        void preHandle_swagger_allowed() {
            request.setRequestURI("/swagger-ui/index.html");

            boolean result = interceptor.preHandle(request, response, null);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("resolveConfig")
    class ResolveConfigTests {

        @Test
        @DisplayName("should use OTP config for auth/otp endpoints")
        void resolveConfig_otpPath_returnsOtpConfig() {
            RateLimitInterceptor.RateLimitConfig config =
                    interceptor.resolveConfig("/v1/auth/otp/request");

            assertThat(config.keySuffix()).isEqualTo("otp");
        }

        @Test
        @DisplayName("should use location config for location/update endpoints")
        void resolveConfig_locationPath_returnsLocationConfig() {
            RateLimitInterceptor.RateLimitConfig config =
                    interceptor.resolveConfig("/v1/location/update");

            assertThat(config.keySuffix()).isEqualTo("loc");
            assertThat(config.window()).isEqualTo(Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("should use default config for other endpoints")
        void resolveConfig_otherPath_returnsDefaultConfig() {
            RateLimitInterceptor.RateLimitConfig config =
                    interceptor.resolveConfig("/v1/employees");

            assertThat(config.keySuffix()).isEqualTo("api");
            assertThat(config.window()).isEqualTo(Duration.ofMinutes(1));
        }
    }

    private void setupAuth(String username) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        username, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
