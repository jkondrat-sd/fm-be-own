package com.fm.be.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Request interceptor that logs request start/completion with timing.
 *
 * <p>Correlation ID and MDC lifecycle are managed by
 * {@link com.fm.be.common.filter.RequestTraceFilter} which runs
 * earlier in the filter chain (before Spring Security).</p>
 */
@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_START_TIME = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());

        log.info("Request started: {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());

        return true;
    }

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
    }
}
