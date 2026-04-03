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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 */
@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Punch in/out with geofence + face verification, attendance reports")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    /**
     * Records an attendance punch for the authenticated employee.
     */
    @Operation(summary = "Punch in/out", description = "Records attendance with geofence validation and optional face match. Roles: EMPLOYEE")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Punch recorded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate punch within 5-min window"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Geofence violation or face mismatch")
    })
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
    @Operation(summary = "Today's attendance", description = "Returns the current day's punch records for the authenticated employee. Roles: EMPLOYEE")
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
    @Operation(summary = "Attendance logs", description = "Paginated attendance logs with optional site/employee filters. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN, CLIENT")
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN', 'CLIENT')")
    public ApiResponse<Object> getAttendanceLogs(
            @Parameter(description = "Filter by site ID") @RequestParam(required = false) UUID siteId,
            @Parameter(description = "Filter by employee ID") @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Start of date range (ISO 8601)", required = true) @RequestParam Instant start,
            @Parameter(description = "End of date range (ISO 8601)", required = true) @RequestParam Instant end,
            @Valid PageRequestDto pageRequest) {
        AttendanceService.PaginatedAttendanceLogs result =
                attendanceService.getAttendanceLogs(
                        siteId, employeeId, start, end, pageRequest);
        return ApiResponse.paginated(result.logs(), result.meta());
    }

    /**
     * Gets an aggregated attendance report for a site and date range.
     */
    @Operation(summary = "Attendance report", description = "Aggregated attendance report for a site and date range. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN, CLIENT")
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
    @Operation(summary = "Site attendance today", description = "Today's attendance records for a specific site. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN")
    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public List<AttendanceLogDto> getSiteAttendance(
            @PathVariable UUID siteId) {
        return attendanceService.getSiteAttendance(siteId);
    }
}
