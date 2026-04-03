package com.fm.be.modules.siteupdates;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.siteupdates.dto.CreateSiteUpdateDto;
import com.fm.be.modules.siteupdates.dto.SiteUpdateResponseDto;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for site update (field report) endpoints.
 */
@RestController
@RequestMapping("/v1/site-updates")
@RequiredArgsConstructor
@Tag(name = "Site Updates", description = "Field site status reports with text + media attachments")
public class SiteUpdateController {

    private final SiteUpdateService siteUpdateService;
    private final EmployeeRepository employeeRepository;

    /**
     * Creates a new site update report from the authenticated employee.
     */
    @Operation(summary = "Create site update", description = "Submits a field status report with optional media URLs. Roles: EMPLOYEE",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Site update created"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLOYEE')")
    public SiteUpdateResponseDto createSiteUpdate(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateSiteUpdateDto dto) {
        Employee employee = employeeRepository
                .findByUserId(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "userId", principal.getUserId()));
        return siteUpdateService.createSiteUpdate(employee.getId(), dto);
    }

    /**
     * Lists site updates for a specific site with pagination.
     */
    @Operation(summary = "List site updates", description = "Paginated list of field reports for a site. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN")
    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public ApiResponse<Object> listBySite(
            @PathVariable UUID siteId, @Valid PageRequestDto pageRequest) {
        SiteUpdateService.PaginatedSiteUpdates result =
                siteUpdateService.listBySite(siteId, pageRequest);
        return ApiResponse.paginated(result.updates(), result.meta());
    }

    /**
     * Gets a single site update by ID.
     */
    @Operation(summary = "Get site update", description = "Retrieves a single field report by ID. Roles: ADMIN, SUPERVISOR, SUPER_ADMIN")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public SiteUpdateResponseDto getSiteUpdate(@PathVariable UUID id) {
        return siteUpdateService.getById(id);
    }
}
