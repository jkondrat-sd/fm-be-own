package com.fm.be.modules.employees;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.constants.DocumentType;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.dto.CreateEmployeeDto;
import com.fm.be.modules.employees.dto.EmployeeDocumentDto;
import com.fm.be.modules.employees.dto.EmployeeResponseDto;
import com.fm.be.modules.employees.dto.UpdateEmployeeDto;
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

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for employee management endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/employees — Create employee (ADMIN, SUPER_ADMIN)</li>
 *   <li>PUT /v1/employees/{id} — Update employee (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/employees/{id} — Get employee (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/employees — List employees (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/employees/search — Search employees (ADMIN, SUPER_ADMIN)</li>
 *   <li>GET /v1/employees/me — Get own profile (EMPLOYEE)</li>
 *   <li>POST /v1/employees/{id}/face — Register face (ADMIN, SUPER_ADMIN)</li>
 *   <li>POST /v1/employees/{id}/documents — Add document (ADMIN, SUPER_ADMIN)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Creates a new employee with associated user account.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponseDto createEmployee(
            @Valid @RequestBody CreateEmployeeDto dto) {
        return employeeService.createEmployee(dto);
    }

    /**
     * Updates an existing employee.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponseDto updateEmployee(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateEmployeeDto dto) {
        return employeeService.updateEmployee(id, dto);
    }

    /**
     * Gets an employee by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponseDto getEmployee(@PathVariable UUID id) {
        return employeeService.getEmployeeById(id);
    }

    /**
     * Lists employees in the authenticated user's organization.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Object> listEmployees(@CurrentUser UserPrincipal principal,
                                             @Valid PageRequestDto pageRequest) {
        EmployeeService.PaginatedEmployees result =
                employeeService.listEmployees(
                        principal.getOrganizationId(), pageRequest);
        return ApiResponse.paginated(result.employees(), result.meta());
    }

    /**
     * Searches employees by name.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Object> searchEmployees(@CurrentUser UserPrincipal principal,
                                               @RequestParam String query,
                                               @Valid PageRequestDto pageRequest) {
        EmployeeService.PaginatedEmployees result =
                employeeService.searchEmployees(
                        query, principal.getOrganizationId(), pageRequest);
        return ApiResponse.paginated(result.employees(), result.meta());
    }

    /**
     * Gets the authenticated employee's own profile.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public EmployeeResponseDto getMyProfile(
            @CurrentUser UserPrincipal principal) {
        return employeeService.getProfileByUserId(principal.getUserId());
    }

    /**
     * Registers a face embedding for an employee.
     */
    @PostMapping("/{id}/face")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> registerFace(@PathVariable UUID id,
                                            @RequestBody float[] embedding) {
        employeeService.registerFace(id, embedding);
        return Map.of("message", "Face registered successfully");
    }

    /**
     * Adds a document to an employee's records.
     */
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeDocumentDto addDocument(@PathVariable UUID id,
                                           @RequestParam DocumentType type,
                                           @RequestParam String fileUrl) {
        return employeeService.addDocument(id, type, fileUrl);
    }
}
