package com.fm.be.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Error details DTO included in the API response envelope when success=false.
 * The error code is a machine-readable identifier shared with all FE clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /** Machine-readable error code (e.g., GEOFENCE_VIOLATION, AUTH_TOKEN_EXPIRED) */
    private String code;

    /** Human-readable error message */
    private String message;

    /** Optional additional context (e.g., { "distance_meters": 150 }) */
    private Map<String, Object> details;
}
