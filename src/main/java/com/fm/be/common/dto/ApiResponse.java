package com.fm.be.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * Standard API response envelope used by ALL endpoints.
 * Guarantees a consistent structure for FE clients.
 *
 * <pre>
 * { "success": true, "traceId": "uuid", "data": {...}, "meta": {...}, "error": null }
 * </pre>
 *
 * @param <T> the type of the response data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;

    /** Correlation/trace ID for request tracing across logs. */
    private String traceId;

    private T data;
    private PaginationMeta meta;
    private ApiError error;

    /**
     * Creates a successful response with data payload.
     * Automatically captures the trace ID from MDC.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .traceId(MDC.get("correlationId"))
                .data(data)
                .build();
    }

    /**
     * Creates a successful paginated response.
     * Automatically captures the trace ID from MDC.
     */
    public static <T> ApiResponse<T> paginated(T data, PaginationMeta meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .traceId(MDC.get("correlationId"))
                .data(data)
                .meta(meta)
                .build();
    }

    /**
     * Creates an error response.
     * Automatically captures the trace ID from MDC.
     */
    public static <T> ApiResponse<T> error(ApiError error) {
        return ApiResponse.<T>builder()
                .success(false)
                .traceId(MDC.get("correlationId"))
                .error(error)
                .build();
    }
}
