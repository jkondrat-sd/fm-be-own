package com.fm.be.modules.payroll;

import com.fm.be.modules.payroll.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the salary_structures table.
 */
@Repository
public interface SalaryStructureRepository
        extends JpaRepository<SalaryStructure, UUID> {

    /**
     * Finds the salary structure for an employee (unique per employee).
     */
    Optional<SalaryStructure> findByEmployeeId(UUID employeeId);

    /**
     * Checks if a salary structure exists for an employee.
     */
    boolean existsByEmployeeId(UUID employeeId);
}
