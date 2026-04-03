package com.fm.be.modules.payroll;

import com.fm.be.modules.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the payslips table.
 */
@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    /**
     * Finds all payslips for a payroll run.
     */
    List<Payslip> findByPayrollRunId(UUID payrollRunId);

    /**
     * Finds all payslips for an employee.
     */
    List<Payslip> findByEmployeeId(UUID employeeId);
}
