package com.fm.be.modules.employees.dto;

import com.fm.be.common.constants.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for creating a new employee.
 * This flow also creates the associated user account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmployeeDto {

    /** Full name of the employee */
    @NotBlank(message = "Full name is required")
    private String fullName;

    /** Phone number for OTP login (E.164 format) */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phone;

    /** Email address (optional for field employees) */
    private String email;

    /** Date of birth */
    private LocalDate dob;

    /** Role to assign (default EMPLOYEE) */
    @Builder.Default
    private Role role = Role.EMPLOYEE;

    /** Site to assign this employee to */
    private UUID siteId;

    /** Organization ID */
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;
}
