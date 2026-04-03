package com.fm.be.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for a salary structure with computed deductions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryStructureResponseDto {

    private UUID id;
    private UUID employeeId;
    private BigDecimal basic;
    private BigDecimal hra;
    private BigDecimal conveyance;
    private BigDecimal special;
    private BigDecimal grossSalary;
    private BigDecimal pfEmployee;
    private BigDecimal pfEmployer;
    private BigDecimal esicEmployee;
    private BigDecimal esicEmployer;
    private BigDecimal ptAmount;
}
