package com.fm.be.modules.attendance;

import com.fm.be.common.constants.PunchType;
import com.fm.be.modules.attendance.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the attendance_logs table.
 * Includes queries for dedup checks, daily reports, and site-scoped lookups.
 */
@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceLog, UUID> {

    /**
     * Finds the most recent punch for an employee to check idempotent
     * deduplication within a time window.
     *
     * @param employeeId the employee UUID
     * @param punchType the punch type (IN/OUT)
     * @param after only consider punches after this instant
     * @return the most recent matching punch, if any
     */
    @Query("SELECT a FROM AttendanceLog a "
            + "WHERE a.employeeId = :employeeId "
            + "AND a.punchType = :punchType "
            + "AND a.punchTime > :after "
            + "ORDER BY a.punchTime DESC LIMIT 1")
    Optional<AttendanceLog> findRecentDuplicate(UUID employeeId,
                                                 PunchType punchType,
                                                 Instant after);

    /**
     * Finds today's punches for an employee.
     *
     * @param employeeId the employee UUID
     * @param dayStart the start of the current day
     * @param dayEnd the end of the current day
     * @return list of punches for today
     */
    List<AttendanceLog> findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
            UUID employeeId, Instant dayStart, Instant dayEnd);

    /**
     * Finds attendance logs for a site within a date range (paginated).
     *
     * @param siteId the site UUID
     * @param start range start
     * @param end range end
     * @param pageable pagination params
     * @return paginated attendance logs
     */
    Page<AttendanceLog> findBySiteIdAndPunchTimeBetween(
            UUID siteId, Instant start, Instant end, Pageable pageable);

    /**
     * Finds attendance logs for an employee within a date range (paginated).
     *
     * @param employeeId the employee UUID
     * @param start range start
     * @param end range end
     * @param pageable pagination params
     * @return paginated attendance logs
     */
    Page<AttendanceLog> findByEmployeeIdAndPunchTimeBetween(
            UUID employeeId, Instant start, Instant end, Pageable pageable);

    /**
     * Counts punches of a given type for a site on a specific day.
     * Used for daily attendance summary reports.
     *
     * @param siteId the site UUID
     * @param punchType the punch type
     * @param start day start
     * @param end day end
     * @return count of matching punches
     */
    long countBySiteIdAndPunchTypeAndPunchTimeBetween(
            UUID siteId, PunchType punchType, Instant start, Instant end);

    /**
     * Finds all attendance logs for a site on a given day,
     * used for site attendance overview.
     */
    List<AttendanceLog> findBySiteIdAndPunchTimeBetweenOrderByPunchTimeDesc(
            UUID siteId, Instant start, Instant end);
}
