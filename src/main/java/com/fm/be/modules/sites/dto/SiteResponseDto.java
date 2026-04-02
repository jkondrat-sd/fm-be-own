package com.fm.be.modules.sites.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for site data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteResponseDto {

    private UUID id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double geofenceRadius;
    private boolean isActive;
    private UUID clientId;
    private UUID organizationId;
    private Instant createdAt;
    private Instant updatedAt;
}
