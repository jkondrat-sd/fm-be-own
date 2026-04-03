package com.fm.be.modules.payroll.entity;

import com.fm.be.common.constants.PayrollStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the payroll_runs table.
 *
 * <p>Represents a payroll processing batch for a specific month/year.
 * Lifecycle: DRAFT → PROCESSING → PENDING_APPROVAL → APPROVED/REJECTED → DISBURSED.
 * UNIQUE constraint on (month, year, organizationId) prevents duplicate runs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payroll_runs",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"month", "year", "organization_id"}))
public class PayrollRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PayrollStatus status = PayrollStatus.DRAFT;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "bank_file_url")
    private String bankFileUrl;

    @Column(name = "total_gross", precision = 14, scale = 2)
    private BigDecimal totalGross;

    @Column(name = "total_net", precision = 14, scale = 2)
    private BigDecimal totalNet;

    @Column(name = "total_pf", precision = 14, scale = 2)
    private BigDecimal totalPf;

    @Column(name = "total_esic", precision = 14, scale = 2)
    private BigDecimal totalEsic;

    @Column(name = "total_tds", precision = 14, scale = 2)
    private BigDecimal totalTds;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

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
