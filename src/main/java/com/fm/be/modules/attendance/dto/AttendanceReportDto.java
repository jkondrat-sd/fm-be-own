package com.fm.be.modules.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for aggregated attendance report data.
 * Used for daily/weekly/monthly summary views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReportDto {

    private UUID siteId;
    private String siteName;
    private Instant periodStart;
    private Instant periodEnd;
    private long totalPunchIns;
    private long totalPunchOuts;
    private long totalEmployees;
    private double averagePunchInsPerDay;
}
