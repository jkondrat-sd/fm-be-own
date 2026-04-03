package com.fm.be.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoggingInterceptor}.
 * Verifies request timing logging. Correlation ID lifecycle is now
 * handled by {@link com.fm.be.common.filter.RequestTraceFilter}.
 */
class LoggingInterceptorTest {

    private LoggingInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new LoggingInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/v1/health");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void preHandle_setsRequestStartTime() {
        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(request).setAttribute(eq("requestStartTime"), anyLong());
    }

    @Test
    void afterCompletion_logsWithDuration() {
        when(request.getAttribute("requestStartTime")).thenReturn(System.currentTimeMillis() - 50);
        when(response.getStatus()).thenReturn(200);

        assertDoesNotThrow(() ->
                interceptor.afterCompletion(request, response, new Object(), null)
        );
    }

    @Test
    void afterCompletion_handlesNullStartTime() {
        when(request.getAttribute("requestStartTime")).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        assertDoesNotThrow(() ->
                interceptor.afterCompletion(request, response, new Object(), null)
        );
    }
}
