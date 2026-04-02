package com.fm.be.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApiResponse} factory methods.
 */
class ApiResponseTest {

    @Test
    void success_createsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("Hello");

        assertTrue(response.isSuccess());
        assertEquals("Hello", response.getData());
        assertNull(response.getMeta());
        assertNull(response.getError());
    }

    @Test
    void success_withNullData() {
        ApiResponse<Object> response = ApiResponse.success(null);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void paginated_includesMeta() {
        PaginationMeta meta = PaginationMeta.builder()
                .page(1)
                .limit(20)
                .total(100)
                .totalPages(5)
                .build();

        ApiResponse<String> response = ApiResponse.paginated("data", meta);

        assertTrue(response.isSuccess());
        assertEquals("data", response.getData());
        assertNotNull(response.getMeta());
        assertEquals(1, response.getMeta().getPage());
        assertEquals(20, response.getMeta().getLimit());
        assertEquals(100, response.getMeta().getTotal());
        assertEquals(5, response.getMeta().getTotalPages());
        assertNull(response.getError());
    }

    @Test
    void error_createsErrorResponse() {
        ApiError apiError = ApiError.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid input")
                .build();

        ApiResponse<Void> response = ApiResponse.error(apiError);

        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertNull(response.getMeta());
        assertNotNull(response.getError());
        assertEquals("VALIDATION_ERROR", response.getError().getCode());
        assertEquals("Invalid input", response.getError().getMessage());
    }
}
