package com.fm.be.modules.attendance;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.attendance.dto.AttendanceLogDto;
import com.fm.be.modules.attendance.dto.AttendanceReportDto;
import com.fm.be.modules.attendance.dto.PunchRequestDto;
import com.fm.be.modules.attendance.dto.PunchResponseDto;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for attendance punch and reporting endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/attendance/punch — Punch in/out (EMPLOYEE)</li>
 *   <li>GET /v1/attendance/today — Get today's punches (EMPLOYEE)</li>
 *   <li>GET /v1/attendance/logs — Paginated logs (ADMIN, SUPERVISOR, CLIENT)</li>
 *   <li>GET /v1/attendance/report — Aggregated report (ADMIN, SUPERVISOR, CLIENT)</li>
 *   <li>GET /v1/attendance/site/{siteId} — Site attendance today (ADMIN, SUPERVISOR)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    /**
     * Records an attendance punch for the authenticated employee.
     */
    @PostMapping("/punch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public PunchResponseDto punch(@CurrentUser UserPrincipal principal,
                                   @Valid @RequestBody PunchRequestDto dto) {
        Employee employee = employeeRepository
                .findByUserId(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "userId", principal.getUserId()));
        return attendanceService.punch(employee.getId(), dto);
    }

    /**
     * Gets today's attendance logs for the authenticated employee.
     */
    @GetMapping("/today")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public List<AttendanceLogDto> getTodayAttendance(
            @CurrentUser UserPrincipal principal) {
        Employee employee = employeeRepository
                .findByUserId(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "userId", principal.getUserId()));
        return attendanceService.getTodayAttendance(employee.getId());
    }

    /**
     * Gets paginated attendance logs, optionally filtered by site/employee.
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN', 'CLIENT')")
    public ApiResponse<Object> getAttendanceLogs(
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @Valid PageRequestDto pageRequest) {
        AttendanceService.PaginatedAttendanceLogs result =
                attendanceService.getAttendanceLogs(
                        siteId, employeeId, start, end, pageRequest);
        return ApiResponse.paginated(result.logs(), result.meta());
    }

    /**
     * Gets an aggregated attendance report for a site and date range.
     */
    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN', 'CLIENT')")
    public AttendanceReportDto getAttendanceReport(
            @RequestParam UUID siteId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        return attendanceService.getAttendanceReport(siteId, start, end);
    }

    /**
     * Gets today's attendance for a specific site.
     */
    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public List<AttendanceLogDto> getSiteAttendance(
            @PathVariable UUID siteId) {
        return attendanceService.getSiteAttendance(siteId);
    }
}
