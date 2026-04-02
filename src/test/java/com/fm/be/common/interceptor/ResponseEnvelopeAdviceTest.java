package com.fm.be.common.interceptor;

import com.fm.be.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ResponseEnvelopeAdvice}.
 */
class ResponseEnvelopeAdviceTest {

    private ResponseEnvelopeAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new ResponseEnvelopeAdvice();
    }

    @Test
    void beforeBodyWrite_wrapsPlainObject() {
        Map<String, String> body = Map.of("key", "value");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/v1/health"));

        Object result = advice.beforeBodyWrite(
                body, null, MediaType.APPLICATION_JSON, null, request, null
        );

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertTrue(apiResponse.isSuccess());
        assertEquals(body, apiResponse.getData());
    }

    @Test
    void beforeBodyWrite_doesNotDoubleWrapApiResponse() {
        ApiResponse<String> alreadyWrapped = ApiResponse.success("data");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/v1/users"));

        Object result = advice.beforeBodyWrite(
                alreadyWrapped, null, MediaType.APPLICATION_JSON, null, request, null
        );

        assertSame(alreadyWrapped, result);
    }

    @Test
    void beforeBodyWrite_skipsActuatorEndpoints() {
        Map<String, String> body = Map.of("status", "UP");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/actuator/health"));

        Object result = advice.beforeBodyWrite(
                body, null, MediaType.APPLICATION_JSON, null, request, null
        );

        // Should NOT be wrapped for actuator endpoints
        assertSame(body, result);
    }

    @Test
    void beforeBodyWrite_skipsSwaggerEndpoints() {
        Map<String, Object> swaggerBody = Map.of("openapi", "3.0");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/v3/api-docs"));

        Object result = advice.beforeBodyWrite(
                swaggerBody, null, MediaType.APPLICATION_JSON, null, request, null
        );

        assertSame(swaggerBody, result);
    }
}
