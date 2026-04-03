package com.fm.be.modules.payroll.dto;

import com.fm.be.common.constants.PayrollStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a payroll run with status and aggregate totals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunResponseDto {

    private UUID id;
    private Integer month;
    private Integer year;
    private PayrollStatus status;
    private UUID processedBy;
    private UUID approvedBy;
    private String bankFileUrl;
    private BigDecimal totalGross;
    private BigDecimal totalNet;
    private BigDecimal totalPf;
    private BigDecimal totalEsic;
    private BigDecimal totalTds;
    private UUID organizationId;
    private Instant createdAt;
    private Instant updatedAt;
}
