package com.fm.be.modules.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for an individual payslip within a payroll run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipResponseDto {

    private UUID id;
    private UUID payrollRunId;
    private UUID employeeId;
    private Integer daysWorked;
    private Integer daysAbsent;
    private BigDecimal grossEarnings;
    private BigDecimal pfDeduction;
    private BigDecimal esicDeduction;
    private BigDecimal tdsDeduction;
    private BigDecimal ptDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal netPayable;
}
