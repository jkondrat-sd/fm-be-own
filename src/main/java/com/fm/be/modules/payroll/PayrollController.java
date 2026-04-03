package com.fm.be.modules.payroll;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.payroll.dto.CreateSalaryStructureDto;
import com.fm.be.modules.payroll.dto.PayrollRunResponseDto;
import com.fm.be.modules.payroll.dto.PayslipResponseDto;
import com.fm.be.modules.payroll.dto.SalaryStructureResponseDto;
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

import java.util.List;
import java.util.UUID;

/**
 * REST controller for payroll management endpoints.
 * Indian statutory compliance: PF, ESIC, TDS, PT.
 */
@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
@Tag(name = "Payroll", description = "Salary structures, payroll runs (async), PF/ESIC/TDS/PT compliance, payslips")
public class PayrollController {

    private final PayrollService payrollService;

    // ====================== Salary Structures ======================

    /**
     * Creates a salary structure for an employee.
     */
    @Operation(summary = "Create salary structure", description = "Creates a salary structure with auto-calculated PF/ESIC/TDS/PT deductions. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Salary structure created"))
    @PostMapping("/salary-structure")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SalaryStructureResponseDto createSalaryStructure(
            @Valid @RequestBody CreateSalaryStructureDto dto) {
        return payrollService.createSalaryStructure(dto);
    }

    /**
     * Gets the salary structure for an employee.
     */
    @Operation(summary = "Get salary structure", description = "Retrieves the salary structure for an employee. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/salary-structure/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SalaryStructureResponseDto getSalaryStructure(
            @PathVariable UUID employeeId) {
        return payrollService.getSalaryStructure(employeeId);
    }

    /**
     * Updates a salary structure (recalculates deductions).
     */
    @Operation(summary = "Update salary structure", description = "Updates salary components and recalculates deductions. Roles: ADMIN, SUPER_ADMIN")
    @PutMapping("/salary-structure/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SalaryStructureResponseDto updateSalaryStructure(
            @PathVariable UUID employeeId,
            @Valid @RequestBody CreateSalaryStructureDto dto) {
        return payrollService.updateSalaryStructure(employeeId, dto);
    }

    // ====================== Payroll Runs ======================

    /**
     * Initiates a payroll run (returns immediately, processing is async).
     */
    @Operation(summary = "Initiate payroll run", description = "Starts async payroll processing. Returns job ID immediately for status polling. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Payroll run initiated"))
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto initiatePayrollRun(
            @CurrentUser UserPrincipal principal,
            @Parameter(description = "Payroll month (1-12)", required = true) @RequestParam int month,
            @Parameter(description = "Payroll year", required = true) @RequestParam int year) {
        return payrollService.initiatePayrollRun(
                month, year,
                principal.getOrganizationId(),
                principal.getUserId());
    }

    /**
     * Gets the status of a payroll run (for polling).
     */
    @Operation(summary = "Get run status", description = "Polls the current status of a payroll run (<50ms). Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/run/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto getRunStatus(@PathVariable UUID id) {
        return payrollService.getPayrollRunStatus(id);
    }

    /**
     * Approves a payroll run.
     */
    @Operation(summary = "Approve payroll run", description = "Approves a PENDING_APPROVAL payroll run. Roles: ADMIN, SUPER_ADMIN")
    @PostMapping("/run/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto approveRun(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal principal) {
        return payrollService.approvePayrollRun(id, principal.getUserId());
    }

    /**
     * Rejects a payroll run.
     */
    @Operation(summary = "Reject payroll run", description = "Rejects a PENDING_APPROVAL payroll run. Roles: ADMIN, SUPER_ADMIN")
    @PostMapping("/run/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto rejectRun(@PathVariable UUID id) {
        return payrollService.rejectPayrollRun(id);
    }

    /**
     * Lists payroll runs for the organization with pagination.
     */
    @Operation(summary = "List payroll runs", description = "Paginated list of payroll runs for the organization. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/runs")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Object> listRuns(
            @CurrentUser UserPrincipal principal,
            @Valid PageRequestDto pageRequest) {
        PayrollService.PaginatedPayrollRuns result =
                payrollService.listPayrollRuns(
                        principal.getOrganizationId(), pageRequest);
        return ApiResponse.paginated(result.runs(), result.meta());
    }

    /**
     * Gets all payslips for a payroll run.
     */
    @Operation(summary = "Get payslips", description = "Returns all generated payslips for a payroll run. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/run/{id}/payslips")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<PayslipResponseDto> getPayslips(@PathVariable UUID id) {
        return payrollService.getPayslipsForRun(id);
    }
}
