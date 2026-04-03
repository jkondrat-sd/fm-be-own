package com.fm.be.modules.admin;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.admin.dto.SystemConfigDto;
import com.fm.be.modules.admin.dto.SystemStatsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for admin-only endpoints (non-user-CRUD).
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Audit logs, dashboard stats, data export (CSV), system configuration")
public class AdminController {

    private final AdminService adminService;

    /**
     * Retrieves paginated audit logs with optional filters.
     *
     * @param userId optional filter by acting user
     * @param action optional filter by action type
     * @param pageRequest pagination parameters
     * @return paginated audit log entries
     */
    @Operation(summary = "Get audit logs", description = "Paginated audit trail with optional user/action filters. Roles: SUPER_ADMIN")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Object> getAuditLogs(
            @Parameter(description = "Filter by acting user ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter by action type (e.g. CREATE_USER)") @RequestParam(required = false) String action,
            @Valid PageRequestDto pageRequest) {
        AdminService.PaginatedAuditLogs result =
                adminService.getAuditLogs(userId, action, pageRequest);
        return ApiResponse.paginated(result.logs(), result.meta());
    }

    /**
     * Returns system-wide dashboard statistics.
     *
     * @return aggregated stats
     */
    @Operation(summary = "Dashboard stats", description = "System-wide statistics: user/employee/site counts, today's attendance, payroll runs. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SystemStatsDto getDashboardStats() {
        return adminService.getDashboardStats();
    }

    /**
     * Exports data for the specified entity type as CSV.
     *
     * @param entity the entity type ("users", "employees", "sites")
     * @param principal the authenticated admin user
     * @param request the HTTP request (for IP logging)
     * @return CSV file as downloadable response
     */
    @Operation(summary = "Export data (CSV)", description = "Exports entity data as a downloadable CSV file. Entities: users, employees, sites. Roles: ADMIN, SUPER_ADMIN")
    @PostMapping("/export/{entity}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportData(
            @Parameter(description = "Entity type: users, employees, or sites") @PathVariable String entity,
            @CurrentUser UserPrincipal principal,
            HttpServletRequest request) {
        String csv = adminService.exportData(
                entity, principal.getUserId(), request.getRemoteAddr());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + entity + "_export.csv");

        return new ResponseEntity<>(csv.getBytes(), headers, HttpStatus.OK);
    }

    /**
     * Retrieves system configuration.
     *
     * @return current system config
     */
    @Operation(summary = "Get system config", description = "Returns current system configuration entries (stored in Redis). Roles: SUPER_ADMIN")
    @GetMapping("/config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SystemConfigDto getSystemConfig() {
        return adminService.getSystemConfig();
    }

    /**
     * Updates system configuration entries.
     *
     * @param dto the config entries to update
     * @param principal the authenticated admin user
     * @param request the HTTP request (for IP logging)
     * @return the updated config
     */
    @Operation(summary = "Update system config", description = "Updates system configuration entries. Audit logged. Roles: SUPER_ADMIN")
    @PutMapping("/config")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SystemConfigDto updateSystemConfig(
            @Valid @RequestBody SystemConfigDto dto,
            @CurrentUser UserPrincipal principal,
            HttpServletRequest request) {
        return adminService.updateSystemConfig(
                dto, principal.getUserId(), request.getRemoteAddr());
    }
}
