package com.fm.be.modules.shifts;

import com.fm.be.modules.shifts.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the shift_assignments table.
 * UNIQUE constraint on (employeeId, date) ensures single assignment per day.
 */
@Repository
public interface ShiftAssignmentRepository
        extends JpaRepository<ShiftAssignment, UUID> {

    /**
     * Checks if an employee already has an assignment on a given date.
     */
    boolean existsByEmployeeIdAndDate(UUID employeeId, LocalDate date);

    /**
     * Finds an employee's assignment for a specific date.
     */
    Optional<ShiftAssignment> findByEmployeeIdAndDate(
            UUID employeeId, LocalDate date);

    /**
     * Finds all assignments for an employee within a date range.
     * Used for the mobile "schedule" / "task view".
     */
    List<ShiftAssignment> findByEmployeeIdAndDateBetween(
            UUID employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * Finds all assignments for a specific shift on a given date.
     */
    List<ShiftAssignment> findByShiftIdAndDate(UUID shiftId, LocalDate date);

    /**
     * Finds all assignments for a given date across all shifts.
     * Used by the shift reminder job.
     */
    List<ShiftAssignment> findByDate(LocalDate date);
}
