package com.fm.be.modules.shifts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

/**
 * DTO for creating a new shift template at a site.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShiftDto {

    @NotNull(message = "Site ID is required")
    private UUID siteId;

    @NotBlank(message = "Shift name is required")
    private String name;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;
}
