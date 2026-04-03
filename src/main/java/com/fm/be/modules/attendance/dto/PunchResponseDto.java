package com.fm.be.modules.attendance.dto;

import com.fm.be.common.constants.PunchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned after a successful attendance punch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PunchResponseDto {

    private UUID id;
    private UUID employeeId;
    private PunchType punchType;
    private Instant punchTime;
    private boolean insideGeofence;
    private Double faceMatchScore;
    private UUID siteId;
}
