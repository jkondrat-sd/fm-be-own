package com.fm.be.modules.payroll.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the salary_structures table.
 *
 * <p>Defines the compensation breakdown for an employee: basic, HRA,
 * allowances, and computed statutory deductions (PF, ESIC, PT).
 * One structure per employee (unique on employeeId).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "salary_structures")
public class SalaryStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private UUID employeeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basic;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hra;

    @Column(precision = 12, scale = 2)
    private BigDecimal conveyance;

    @Column(precision = 12, scale = 2)
    private BigDecimal special;

    @Column(name = "gross_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "pf_employee", precision = 12, scale = 2)
    private BigDecimal pfEmployee;

    @Column(name = "pf_employer", precision = 12, scale = 2)
    private BigDecimal pfEmployer;

    @Column(name = "esic_employee", precision = 12, scale = 2)
    private BigDecimal esicEmployee;

    @Column(name = "esic_employer", precision = 12, scale = 2)
    private BigDecimal esicEmployer;

    @Column(name = "pt_amount", precision = 12, scale = 2)
    private BigDecimal ptAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
