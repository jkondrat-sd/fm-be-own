package com.fm.be.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for system-wide dashboard statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsDto {

    private long totalUsers;
    private long activeUsers;
    private long totalEmployees;
    private long totalSites;
    private long activeSites;
    private long totalClients;
    private long todayAttendanceCount;
    private long totalPayrollRuns;
}
