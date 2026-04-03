package com.fm.be.modules.shifts;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.shifts.dto.CreateShiftDto;
import com.fm.be.modules.shifts.dto.ScheduleResponseDto;
import com.fm.be.modules.shifts.dto.ShiftAssignmentDto;
import com.fm.be.modules.shifts.dto.UpdateShiftDto;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for shift scheduling and assignment endpoints.
 */
@RestController
@RequestMapping("/v1/shifts")
@RequiredArgsConstructor
@Tag(name = "Shifts", description = "Shift scheduling, employee assignment, and mobile task view")
public class ShiftController {

    private final ShiftService shiftService;
    private final EmployeeRepository employeeRepository;

    /**
     * Creates a new shift template.
     */
    @Operation(summary = "Create shift", description = "Creates a new shift template for a site. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Shift created"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Shift createShift(@Valid @RequestBody CreateShiftDto dto) {
        return shiftService.createShift(dto);
    }

    /**
     * Updates an existing shift template.
     */
    @Operation(summary = "Update shift", description = "Updates shift name, times, or active status. Roles: ADMIN, SUPER_ADMIN")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Shift updateShift(@PathVariable UUID id,
                             @Valid @RequestBody UpdateShiftDto dto) {
        return shiftService.updateShift(id, dto);
    }

    /**
     * Lists shifts for a site with pagination.
     */
    @Operation(summary = "List site shifts", description = "Paginated list of shift templates for a site. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN")
    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public ApiResponse<Object> listShifts(
            @PathVariable UUID siteId, @Valid PageRequestDto pageRequest) {
        ShiftService.PaginatedShifts result =
                shiftService.listShifts(siteId, pageRequest);
        return ApiResponse.paginated(result.shifts(), result.meta());
    }

    /**
     * Assigns an employee to a shift on a specific date.
     */
    @Operation(summary = "Assign employee to shift", description = "Assigns an employee to a shift on a date. UNIQUE(employeeId, date). Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Assignment created"))
    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ShiftAssignment assignEmployee(
            @Valid @RequestBody ShiftAssignmentDto dto) {
        return shiftService.assignEmployee(dto);
    }

    /**
     * Gets the schedule for the authenticated employee or a specified employee.
     * Employees can only view their own schedule.
     */
    @Operation(summary = "Get schedule", description = "Returns shift schedule for a date range. Employees see own; admins can specify employeeId.")
    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public List<ScheduleResponseDto> getSchedule(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "Employee ID (admin only, omit for own schedule)") @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Start date (YYYY-MM-DD)", required = true) @RequestParam LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)", required = true) @RequestParam LocalDate endDate) {

        UUID targetEmployeeId;
        if (employeeId != null
                && principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPERVISOR"))) {
            targetEmployeeId = employeeId;
        } else {
            // Employees view their own schedule
            Employee employee = employeeRepository
                    .findByUserId(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Employee", "userId", principal.getUserId()));
            targetEmployeeId = employee.getId();
        }

        return shiftService.getSchedule(
                targetEmployeeId, startDate, endDate);
    }
}
