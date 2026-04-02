package com.fm.be.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LoggingInterceptor}.
 * Verifies MDC correlation ID lifecycle and request timing logging.
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

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void preHandle_setsCorrelationIdInMdc() {
        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        String correlationId = MDC.get(LoggingInterceptor.CORRELATION_ID_MDC_KEY);
        assertNotNull(correlationId);
        // UUID format check
        assertEquals(36, correlationId.length());
    }

    @Test
    void preHandle_setsCorrelationIdOnResponse() {
        interceptor.preHandle(request, response, new Object());

        verify(response).setHeader(eq(LoggingInterceptor.CORRELATION_ID_HEADER), anyString());
    }

    @Test
    void preHandle_setsRequestStartTime() {
        interceptor.preHandle(request, response, new Object());

        verify(request).setAttribute(eq("requestStartTime"), anyLong());
    }

    @Test
    void afterCompletion_clearsMdc() {
        // Set up MDC via preHandle
        interceptor.preHandle(request, response, new Object());
        assertNotNull(MDC.get(LoggingInterceptor.CORRELATION_ID_MDC_KEY));

        // Configure for afterCompletion
        when(request.getAttribute("requestStartTime")).thenReturn(System.currentTimeMillis() - 50);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        // MDC should be cleared
        assertNull(MDC.get(LoggingInterceptor.CORRELATION_ID_MDC_KEY));
    }

    @Test
    void afterCompletion_handlesNullStartTime() {
        when(request.getAttribute("requestStartTime")).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        // Should not throw
        assertDoesNotThrow(() ->
                interceptor.afterCompletion(request, response, new Object(), null)
        );
    }
}
