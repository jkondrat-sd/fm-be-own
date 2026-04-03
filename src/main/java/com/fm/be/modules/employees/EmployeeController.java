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

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for employee management endpoints.
 */
@RestController
@RequestMapping("/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee onboarding, profiles, documents, and face registration")
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Creates a new employee with associated user account.
     */
    @Operation(summary = "Create employee", description = "Creates a new employee with associated user account. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Employee created"))
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
    @Operation(summary = "Update employee", description = "Updates employee profile fields. Roles: ADMIN, SUPER_ADMIN")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponseDto updateEmployee(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateEmployeeDto dto) {
        return employeeService.updateEmployee(id, dto);
    }

    /**
     * Gets an employee by ID.
     */
    @Operation(summary = "Get employee", description = "Retrieves an employee profile by ID. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeResponseDto getEmployee(@PathVariable UUID id) {
        return employeeService.getEmployeeById(id);
    }

    /**
     * Lists employees in the authenticated user's organization.
     */
    @Operation(summary = "List employees", description = "Paginated list of employees in the organization. Roles: ADMIN, SUPER_ADMIN")
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
    @Operation(summary = "Search employees", description = "Search employees by name within the organization. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Object> searchEmployees(@CurrentUser UserPrincipal principal,
                                               @Parameter(description = "Name search query") @RequestParam String query,
                                               @Valid PageRequestDto pageRequest) {
        EmployeeService.PaginatedEmployees result =
                employeeService.searchEmployees(
                        query, principal.getOrganizationId(), pageRequest);
        return ApiResponse.paginated(result.employees(), result.meta());
    }

    /**
     * Gets the authenticated employee's own profile.
     */
    @Operation(summary = "Get own profile", description = "Returns the authenticated employee's own profile. Roles: EMPLOYEE")
    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public EmployeeResponseDto getMyProfile(
            @CurrentUser UserPrincipal principal) {
        return employeeService.getProfileByUserId(principal.getUserId());
    }

    /**
     * Registers a face embedding for an employee.
     */
    @Operation(summary = "Register face", description = "Stores a face embedding vector for facial verification. Roles: ADMIN, SUPER_ADMIN")
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
    @Operation(summary = "Add document", description = "Attaches a verified document (Aadhaar, PAN, etc.) to an employee record. Roles: ADMIN, SUPER_ADMIN")
    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public EmployeeDocumentDto addDocument(@PathVariable UUID id,
                                           @Parameter(description = "Document type") @RequestParam DocumentType type,
                                           @Parameter(description = "URL of the uploaded document") @RequestParam String fileUrl) {
        return employeeService.addDocument(id, type, fileUrl);
    }
}
