package com.fm.be.modules.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for geofence violation alerts.
 * Triggered when an employee is outside their assigned site for >5 minutes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceAlertDto {

    private UUID employeeId;
    private String employeeName;
    private UUID siteId;
    private String siteName;
    private Double lastLatitude;
    private Double lastLongitude;
    private Instant detectedAt;
    /** Duration in minutes that the employee has been outside */
    private long minutesOutside;
}
