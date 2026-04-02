package com.fm.be.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shared pagination request parameters.
 * Used by controllers to accept page/limit query parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequestDto {

    /** Page number (1-indexed, default 1) */
    @Min(value = 1, message = "Page must be at least 1")
    @Builder.Default
    private int page = 1;

    /** Items per page (default 20, max 100) */
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    @Builder.Default
    private int limit = 20;

    /** Optional sort field */
    private String sortBy;

    /** Sort direction: ASC or DESC (default DESC) */
    @Builder.Default
    private String sortDirection = "DESC";
}
