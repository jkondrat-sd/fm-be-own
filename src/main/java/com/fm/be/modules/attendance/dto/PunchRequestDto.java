package com.fm.be.modules.attendance.dto;

import com.fm.be.common.constants.PunchType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for submitting an attendance punch (in or out).
 *
 * <p>The mobile app sends GPS coordinates, punch type,
 * an optional face image URL for verification, and device metadata.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PunchRequestDto {

    @NotNull(message = "Punch type is required")
    private PunchType punchType;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    /** GPS accuracy in meters */
    private Double accuracy;

    /** URL of the face image captured during punch (for verification) */
    private String faceImageUrl;

    /** Face match score from client-side verification (0.0 – 1.0) */
    private Double faceMatchScore;

    /** Timestamp from the device (may differ from server time) */
    private Instant timestamp;

    /** Device metadata: model, os, appVersion, etc. */
    private Map<String, Object> deviceInfo;
}
