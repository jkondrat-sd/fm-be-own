package com.fm.be.modules.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for live location data read from Redis.
 * Represents the most recent known position of an employee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveLocationDto {

    private UUID employeeId;
    private String employeeName;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double battery;
    private Double signal;
    private boolean airplaneMode;
    private Instant lastUpdated;
}
