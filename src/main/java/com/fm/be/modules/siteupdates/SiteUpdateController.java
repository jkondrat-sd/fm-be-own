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
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/site-updates — Create update (EMPLOYEE)</li>
 *   <li>GET /v1/site-updates/site/{siteId} — List by site (ADMIN, SUPERVISOR)</li>
 *   <li>GET /v1/site-updates/{id} — Get single update (ADMIN, SUPERVISOR)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/site-updates")
@RequiredArgsConstructor
public class SiteUpdateController {

    private final SiteUpdateService siteUpdateService;
    private final EmployeeRepository employeeRepository;

    /**
     * Creates a new site update report from the authenticated employee.
     */
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
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'SUPER_ADMIN')")
    public SiteUpdateResponseDto getSiteUpdate(@PathVariable UUID id) {
        return siteUpdateService.getById(id);
    }
}
