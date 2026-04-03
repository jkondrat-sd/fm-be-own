package com.fm.be.modules.shifts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Response DTO for a schedule entry — combines shift details with assignment date.
 * Serves as the mobile employee's "task view" data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponseDto {

    private UUID assignmentId;
    private UUID shiftId;
    private String shiftName;
    private UUID siteId;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate date;
}
