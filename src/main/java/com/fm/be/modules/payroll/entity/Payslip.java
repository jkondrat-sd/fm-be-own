package com.fm.be.modules.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the payslips table.
 *
 * <p>Each payslip belongs to a payroll run and records the
 * computed earnings and deductions for one employee.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payroll_run_id", nullable = false)
    private UUID payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "days_worked")
    private Integer daysWorked;

    @Column(name = "days_absent")
    private Integer daysAbsent;

    @Column(name = "gross_earnings", precision = 12, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "pf_deduction", precision = 12, scale = 2)
    private BigDecimal pfDeduction;

    @Column(name = "esic_deduction", precision = 12, scale = 2)
    private BigDecimal esicDeduction;

    @Column(name = "tds_deduction", precision = 12, scale = 2)
    private BigDecimal tdsDeduction;

    @Column(name = "pt_deduction", precision = 12, scale = 2)
    private BigDecimal ptDeduction;

    @Column(name = "other_deductions", precision = 12, scale = 2)
    private BigDecimal otherDeductions;

    @Column(name = "net_payable", precision = 12, scale = 2)
    private BigDecimal netPayable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
