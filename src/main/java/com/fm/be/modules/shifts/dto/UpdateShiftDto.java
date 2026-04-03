package com.fm.be.modules.shifts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * DTO for updating an existing shift template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShiftDto {

    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isActive;
}
