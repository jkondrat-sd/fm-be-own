package com.fm.be.modules.location.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for submitting a location update from the mobile app.
 * Written to Redis on every request; batched to PostgreSQL every 5 min.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateDto {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    /** GPS accuracy in meters */
    private Double accuracy;

    /** Battery level (0.0 – 1.0) */
    private Double battery;

    /** Signal strength */
    private Double signal;

    /** Whether airplane mode is enabled */
    @Builder.Default
    private boolean airplaneMode = false;
}
