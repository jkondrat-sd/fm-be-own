package com.fm.be.common.interceptor;

import com.fm.be.common.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Automatically wraps all controller response bodies in the standard
 * {@link ApiResponse} envelope. Skips wrapping if the response is
 * already an ApiResponse or a String (to avoid double-wrapping).
 *
 * <p>This ensures every endpoint returns a consistent structure
 * without controllers needing to manually wrap responses.</p>
 */
@RestControllerAdvice(basePackages = "com.fm.be")
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Apply to all controller responses except Strings
     * (String responses need special handling due to StringHttpMessageConverter).
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // Don't wrap String responses to avoid ClassCastException
        if (returnType.getParameterType().equals(String.class)) {
            return false;
        }
        // Don't wrap byte[] responses to avoid ClassCastException by ByteArrayHttpMessageConverter
        if (converterType.equals(org.springframework.http.converter.ByteArrayHttpMessageConverter.class) ||
            returnType.getParameterType().equals(byte[].class)) {
            return false;
        }
        return true;
    }

    /**
     * Wraps the response body in ApiResponse.success() if not already wrapped.
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // Don't double-wrap if already an ApiResponse
        if (body instanceof ApiResponse) {
            return body;
        }
        
        // Don't wrap byte arrays (e.g., CSV or file downloads)
        if (body instanceof byte[]) {
            return body;
        }

        // Skip wrapping for Actuator and Swagger endpoints
        String path = request.getURI().getPath();
        if (path.startsWith("/actuator") || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")) {
            return body;
        }

        return ApiResponse.success(body);
    }
}
