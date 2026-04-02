package com.fm.be.modules.sites.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for setting a site's geofence.
 * Supports circular geofence (lat/lng/radius) and polygon GeoJSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceDto {

    /** Geofence center latitude (for circular) */
    @NotNull(message = "Latitude is required")
    private Double latitude;

    /** Geofence center longitude (for circular) */
    @NotNull(message = "Longitude is required")
    private Double longitude;

    /** Radius in meters (for circular geofence; null if using polygon) */
    private Double radiusMeters;

    /** GeoJSON polygon string (from Leaflet Draw; null if using circular) */
    private String geoJson;
}
