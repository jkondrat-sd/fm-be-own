package com.fm.be.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination metadata included in paginated API responses.
 * Supports offset-based pagination for admin data tables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMeta {

    /** Current page number (1-indexed) */
    private int page;

    /** Number of items per page */
    private int limit;

    /** Total number of items across all pages */
    private long total;

    /** Total number of pages */
    private int totalPages;
}
