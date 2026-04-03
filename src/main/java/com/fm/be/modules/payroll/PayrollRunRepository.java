package com.fm.be.modules.payroll;

import com.fm.be.modules.payroll.entity.PayrollRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the payroll_runs table.
 */
@Repository
public interface PayrollRunRepository
        extends JpaRepository<PayrollRun, UUID> {

    /**
     * Finds a payroll run by month/year/org (UNIQUE).
     */
    Optional<PayrollRun> findByMonthAndYearAndOrganizationId(
            Integer month, Integer year, UUID organizationId);

    /**
     * Lists payroll runs for an organization (paginated).
     */
    Page<PayrollRun> findByOrganizationId(
            UUID organizationId, Pageable pageable);

    /**
     * Checks if a run exists for a given period.
     */
    boolean existsByMonthAndYearAndOrganizationId(
            Integer month, Integer year, UUID organizationId);
}
