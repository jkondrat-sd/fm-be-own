package com.fm.be.modules.employees.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for updating an existing employee.
 * All fields are optional — only non-null fields are updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEmployeeDto {

    /** Update full name */
    private String fullName;

    /** Update date of birth */
    private LocalDate dob;

    /** Reassign to a different site */
    private UUID siteId;
}
