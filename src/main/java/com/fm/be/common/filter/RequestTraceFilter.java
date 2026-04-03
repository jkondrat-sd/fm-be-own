package com.fm.be.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes a correlation/trace ID for every request.
 *
 * <p>Runs before Spring Security so that even 401/403 rejections carry
 * a trace ID in the response header and in every log line via MDC.</p>
 *
 * <p><b>Behaviour:</b></p>
 * <ul>
 *   <li>If the client sends {@code X-Correlation-Id} or {@code X-Request-Id},
 *       that value is honoured (enables end-to-end tracing from FE/mobile).</li>
 *   <li>Otherwise a new UUID v4 is generated.</li>
 *   <li>The resolved ID is placed in SLF4J MDC under key {@code correlationId}
 *       and returned in the {@code X-Correlation-Id} response header.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);

        MDC.put(MDC_KEY, traceId);
        response.setHeader(CORRELATION_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String id = request.getHeader(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(id)) {
            return id.trim();
        }
        id = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(id)) {
            return id.trim();
        }
        return UUID.randomUUID().toString();
    }
}
