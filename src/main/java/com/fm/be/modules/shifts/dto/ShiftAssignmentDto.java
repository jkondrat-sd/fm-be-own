package com.fm.be.modules.shifts.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for assigning an employee to a shift on a specific date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAssignmentDto {

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    @NotNull(message = "Shift ID is required")
    private UUID shiftId;

    @NotNull(message = "Date is required")
    private LocalDate date;
}
