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
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/shifts — Create shift (ADMIN, SUPER_ADMIN)</li>
 *   <li>PUT /v1/shifts/{id} — Update shift (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/shifts/site/{siteId} — List shifts for site (ADMIN, SUPERVISOR)</li>
 *   <li>POST /v1/shifts/assign — Assign employee (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/shifts/schedule — Get schedule (EMPLOYEE for own, ADMIN for any)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;
    private final EmployeeRepository employeeRepository;

    /**
     * Creates a new shift template.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Shift createShift(@Valid @RequestBody CreateShiftDto dto) {
        return shiftService.createShift(dto);
    }

    /**
     * Updates an existing shift template.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Shift updateShift(@PathVariable UUID id,
                             @Valid @RequestBody UpdateShiftDto dto) {
        return shiftService.updateShift(id, dto);
    }

    /**
     * Lists shifts for a site with pagination.
     */
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
    @GetMapping("/schedule")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public List<ScheduleResponseDto> getSchedule(
            @CurrentUser UserPrincipal principal,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

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
