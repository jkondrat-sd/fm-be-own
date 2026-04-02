package com.fm.be.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response envelope used by ALL endpoints.
 * Guarantees a consistent structure for FE clients.
 *
 * <pre>
 * { "success": true, "data": {...}, "meta": {...}, "error": null }
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
    private T data;
    private PaginationMeta meta;
    private ApiError error;

    /**
     * Creates a successful response with data payload.
     *
     * @param data the response body
     * @param <T>  type of the data
     * @return wrapped ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * Creates a successful paginated response.
     *
     * @param data the response body
     * @param meta pagination metadata
     * @param <T>  type of the data
     * @return wrapped ApiResponse with pagination
     */
    public static <T> ApiResponse<T> paginated(T data, PaginationMeta meta) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(meta)
                .build();
    }

    /**
     * Creates an error response.
     *
     * @param error the error details
     * @param <T>   type placeholder (data will be null)
     * @return wrapped ApiResponse with error
     */
    public static <T> ApiResponse<T> error(ApiError error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
    }
}
