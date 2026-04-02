package com.fm.be.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Request interceptor that generates a UUID correlation ID per request,
 * stores it in MDC for structured logging, and logs request timing.
 *
 * <p>The correlation ID is also returned in the response header
 * {@code X-Correlation-Id} for client-side troubleshooting.</p>
 */
@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String REQUEST_START_TIME = "requestStartTime";

    /**
     * Generates a correlation ID and logs the request start.
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        log.info("Request started: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        return true;
    }

    /**
     * Logs request completion with duration and cleans up MDC.
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

        log.info("Request completed: {} {} — status={} duration={}ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);

        MDC.clear();
    }
}
