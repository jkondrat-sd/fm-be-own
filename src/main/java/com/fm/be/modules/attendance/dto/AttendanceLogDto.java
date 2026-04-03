package com.fm.be.modules.attendance.dto;

import com.fm.be.common.constants.PunchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for a single attendance log entry in lists and reports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLogDto {

    private UUID id;
    private UUID employeeId;
    private PunchType punchType;
    private Instant punchTime;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double faceMatchScore;
    private boolean insideGeofence;
    private Map<String, Object> deviceInfo;
    private UUID siteId;
    private Instant createdAt;
}
