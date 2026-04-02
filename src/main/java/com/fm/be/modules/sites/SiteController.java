package com.fm.be.modules.sites;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.sites.dto.CreateSiteDto;
import com.fm.be.modules.sites.dto.GeofenceDto;
import com.fm.be.modules.sites.dto.SiteResponseDto;
import com.fm.be.modules.sites.dto.UpdateSiteDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for site management endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /v1/sites — Create site</li>
 *   <li>PUT /v1/sites/{id} — Update site</li>
 *   <li>GET /v1/sites/{id} — Get site</li>
 *   <li>GET /v1/sites — List sites (paginated)</li>
 *   <li>PUT /v1/sites/{id}/geofence — Set geofence</li>
 *   <li>DELETE /v1/sites/{id} — Deactivate site (soft delete)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    /**
     * Creates a new site.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SiteResponseDto createSite(@Valid @RequestBody CreateSiteDto dto) {
        return siteService.createSite(dto);
    }

    /**
     * Updates an existing site.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public SiteResponseDto updateSite(@PathVariable UUID id,
                                      @CurrentUser UserPrincipal principal,
                                      @Valid @RequestBody UpdateSiteDto dto) {
        return siteService.updateSite(id, principal.getOrganizationId(), dto);
    }

    /**
     * Gets a site by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public SiteResponseDto getSite(@PathVariable UUID id,
                                   @CurrentUser UserPrincipal principal) {
        return siteService.getSiteById(id, principal.getOrganizationId());
    }

    /**
     * Lists sites in the authenticated user's organization.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SUPERVISOR')")
    public ApiResponse<Object> listSites(@CurrentUser UserPrincipal principal,
                                         @Valid PageRequestDto pageRequest) {
        SiteService.PaginatedSites result =
                siteService.listSites(
                        principal.getOrganizationId(), pageRequest);
        return ApiResponse.paginated(result.sites(), result.meta());
    }

    /**
     * Sets the geofence for a site (circular or polygon).
     */
    @PutMapping("/{id}/geofence")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Map<String, String> setGeofence(@PathVariable UUID id,
                                           @CurrentUser UserPrincipal principal,
                                           @Valid @RequestBody GeofenceDto dto) {
        siteService.setGeofence(id, principal.getOrganizationId(), dto);
        return Map.of("message", "Geofence updated successfully");
    }

    /**
     * Deactivates a site (soft delete).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void deactivateSite(@PathVariable UUID id,
                               @CurrentUser UserPrincipal principal) {
        siteService.deactivateSite(id, principal.getOrganizationId());
    }
}
