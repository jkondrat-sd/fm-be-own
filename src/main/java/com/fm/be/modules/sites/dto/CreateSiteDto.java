package com.fm.be.modules.sites.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for creating a new site.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSiteDto {

    /** Site name */
    @NotBlank(message = "Site name is required")
    private String name;

    /** Site address */
    private String address;

    /** Geofence center latitude */
    private Double latitude;

    /** Geofence center longitude */
    private Double longitude;

    /** Geofence radius in meters (for circular geofence) */
    private Double geofenceRadius;

    /** Client this site belongs to (optional) */
    private UUID clientId;

    /** Organization ID */
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;
}
