package com.fm.be.modules.admin;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.admin.dto.SystemConfigDto;
import com.fm.be.modules.admin.dto.SystemStatsDto;
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
 *
 * <p>User CRUD is handled by {@link com.fm.be.modules.users.UserController}
 * at {@code /v1/admin/users}. This controller provides:</p>
 * <ul>
 *   <li>GET    /v1/admin/audit-logs — Audit trail (paginated)</li>
 *   <li>GET    /v1/admin/dashboard/stats — System-wide statistics</li>
 *   <li>POST   /v1/admin/export/{entity} — Export data (CSV)</li>
 *   <li>GET    /v1/admin/config — System configuration</li>
 *   <li>PUT    /v1/admin/config — Update system configuration</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
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
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Object> getAuditLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
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
    @PostMapping("/export/{entity}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportData(
            @PathVariable String entity,
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
