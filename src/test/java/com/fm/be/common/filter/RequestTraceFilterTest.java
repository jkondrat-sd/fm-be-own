package com.fm.be.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RequestTraceFilter}.
 */
class RequestTraceFilterTest {

    private RequestTraceFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestTraceFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void generatesNewUuidWhenNoHeaderPresent() throws Exception {
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq(RequestTraceFilter.CORRELATION_ID_HEADER), argThat(id -> {
            assertNotNull(id);
            assertEquals(36, id.length());
            return true;
        }));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void honoursIncomingCorrelationIdHeader() throws Exception {
        String clientId = "client-trace-abc-123";
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(clientId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(RequestTraceFilter.CORRELATION_ID_HEADER, clientId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void honoursIncomingRequestIdHeader() throws Exception {
        String requestId = "req-id-xyz-789";
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn(requestId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(RequestTraceFilter.CORRELATION_ID_HEADER, requestId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void correlationIdHeaderTakesPrecedenceOverRequestId() throws Exception {
        String correlationId = "corr-id-primary";
        String requestId = "req-id-fallback";
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(correlationId);
        when(request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn(requestId);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(RequestTraceFilter.CORRELATION_ID_HEADER, correlationId);
    }

    @Test
    void clearsMdcAfterFilterChainCompletes() throws Exception {
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));
    }

    @Test
    void clearsMdcEvenWhenFilterChainThrows() throws Exception {
        when(request.getHeader(RequestTraceFilter.CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getHeader(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn(null);
        doThrow(new RuntimeException("downstream error"))
                .when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () ->
                filter.doFilterInternal(request, response, filterChain)
        );
        assertNull(MDC.get(RequestTraceFilter.MDC_KEY));
    }
}
