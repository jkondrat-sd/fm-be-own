package com.fm.be.modules.payroll;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.payroll.dto.CreateSalaryStructureDto;
import com.fm.be.modules.payroll.dto.PayrollRunResponseDto;
import com.fm.be.modules.payroll.dto.PayslipResponseDto;
import com.fm.be.modules.payroll.dto.SalaryStructureResponseDto;
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
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/payroll/salary-structure — Create salary structure</li>
 *   <li>GET /v1/payroll/salary-structure/{employeeId} — Get structure</li>
 *   <li>PUT /v1/payroll/salary-structure/{employeeId} — Update structure</li>
 *   <li>POST /v1/payroll/run — Initiate payroll run</li>
 *   <li>GET /v1/payroll/run/{id}/status — Get run status (for polling)</li>
 *   <li>POST /v1/payroll/run/{id}/approve — Approve run</li>
 *   <li>POST /v1/payroll/run/{id}/reject — Reject run</li>
 *   <li>GET /v1/payroll/runs — List runs (paginated)</li>
 *   <li>GET /v1/payroll/run/{id}/payslips — Get payslips for a run</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    // ====================== Salary Structures ======================

    /**
     * Creates a salary structure for an employee.
     */
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
    @GetMapping("/salary-structure/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SalaryStructureResponseDto getSalaryStructure(
            @PathVariable UUID employeeId) {
        return payrollService.getSalaryStructure(employeeId);
    }

    /**
     * Updates a salary structure (recalculates deductions).
     */
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
    @PostMapping("/run")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto initiatePayrollRun(
            @CurrentUser UserPrincipal principal,
            @RequestParam int month,
            @RequestParam int year) {
        return payrollService.initiatePayrollRun(
                month, year,
                principal.getOrganizationId(),
                principal.getUserId());
    }

    /**
     * Gets the status of a payroll run (for polling).
     */
    @GetMapping("/run/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto getRunStatus(@PathVariable UUID id) {
        return payrollService.getPayrollRunStatus(id);
    }

    /**
     * Approves a payroll run.
     */
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
    @PostMapping("/run/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PayrollRunResponseDto rejectRun(@PathVariable UUID id) {
        return payrollService.rejectPayrollRun(id);
    }

    /**
     * Lists payroll runs for the organization with pagination.
     */
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
    @GetMapping("/run/{id}/payslips")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public List<PayslipResponseDto> getPayslips(@PathVariable UUID id) {
        return payrollService.getPayslipsForRun(id);
    }
}
