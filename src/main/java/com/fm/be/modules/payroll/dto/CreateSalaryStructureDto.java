package com.fm.be.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for creating/updating a salary structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalaryStructureDto {

    @NotNull(message = "Employee ID is required")
    private UUID employeeId;

    @NotNull(message = "Basic salary is required")
    @Positive(message = "Basic salary must be positive")
    private BigDecimal basic;

    @NotNull(message = "HRA is required")
    @Positive(message = "HRA must be positive")
    private BigDecimal hra;

    private BigDecimal conveyance;
    private BigDecimal special;
}
