package com.fm.be.modules.location;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.location.dto.GeofenceAlertDto;
import com.fm.be.modules.location.dto.HeatmapDto;
import com.fm.be.modules.location.dto.LiveLocationDto;
import com.fm.be.modules.location.dto.LocationUpdateDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for location tracking endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/location/update — Submit location (EMPLOYEE)</li>
 *   <li>GET /v1/location/live/{siteId} — Live locations (SUPERVISOR, ADMIN)</li>
 *   <li>GET /v1/location/heatmap/{siteId} — Heatmap data (ADMIN)</li>
 *   <li>GET /v1/location/alerts — Geofence alerts (SUPERVISOR, ADMIN)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final EmployeeRepository employeeRepository;

    /**
     * Submits a location update from the authenticated employee.
     * Written to Redis only for fast response (&lt;100ms target).
     */
    @PostMapping("/update")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public Map<String, String> updateLocation(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody LocationUpdateDto dto) {
        Employee employee = employeeRepository
                .findByUserId(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "userId", principal.getUserId()));
        locationService.handleLocationUpdate(employee.getId(), dto);
        return Map.of("status", "ok");
    }

    /**
     * Gets live locations for all employees at a site.
     */
    @GetMapping("/live/{siteId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN', 'SUPER_ADMIN')")
    public List<LiveLocationDto> getLiveLocations(
            @PathVariable UUID siteId) {
        return locationService.getLiveLocations(siteId);
    }

    /**
     * Gets heatmap data for a site within a date range.
     */
    @GetMapping("/heatmap/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public HeatmapDto getHeatmapData(
            @PathVariable UUID siteId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        return locationService.getHeatmapData(siteId, start, end);
    }

    /**
     * Gets geofence violation alerts for the authenticated user's org.
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN', 'SUPER_ADMIN')")
    public List<GeofenceAlertDto> getAlerts(
            @CurrentUser UserPrincipal principal) {
        return locationService.getAlerts(principal.getOrganizationId());
    }
}
