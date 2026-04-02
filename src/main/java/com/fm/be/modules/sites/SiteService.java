package com.fm.be.modules.sites;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.dto.EmployeeResponseDto;
import com.fm.be.modules.sites.dto.CreateSiteDto;
import com.fm.be.modules.sites.dto.GeofenceDto;
import com.fm.be.modules.sites.dto.SiteResponseDto;
import com.fm.be.modules.sites.dto.UpdateSiteDto;
import com.fm.be.modules.sites.entity.Site;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for site CRUD, geofence management, and employee listing.
 *
 * <p>Sites support circular geofences (via lat/lng/radius → PostGIS ST_Buffer)
 * and polygon geofences (via GeoJSON → PostGIS ST_GeomFromGeoJSON). Geofences
 * are cached in Redis and invalidated on update.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    private static final String GEOFENCE_CACHE_PREFIX = "site:%s:geofence";

    private final SiteRepository siteRepository;
    private final EmployeeRepository employeeRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Creates a new site. Optionally sets a circular geofence if
     * lat/lng/radius are provided.
     *
     * @param dto the site creation data
     * @return the created site response
     */
    @Transactional
    public SiteResponseDto createSite(CreateSiteDto dto) {
        Site site = Site.builder()
                .name(dto.getName())
                .address(dto.getAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .geofenceRadius(dto.getGeofenceRadius())
                .clientId(dto.getClientId())
                .organizationId(dto.getOrganizationId())
                .build();

        site = siteRepository.save(site);

        // Set circular geofence if coordinates provided
        if (dto.getLatitude() != null && dto.getLongitude() != null
                && dto.getGeofenceRadius() != null) {
            siteRepository.setCircularGeofence(
                    site.getId(), dto.getLatitude(), dto.getLongitude(),
                    dto.getGeofenceRadius());
        }

        log.info("Site created: id={}, name={}", site.getId(), site.getName());
        return toResponseDto(site);
    }

    /**
     * Updates an existing site. Only non-null fields are updated.
     *
     * @param id the site UUID
     * @param organizationId the org UUID (for scoping)
     * @param dto the update data
     * @return the updated site response
     */
    @Transactional
    public SiteResponseDto updateSite(UUID id, UUID organizationId,
                                      UpdateSiteDto dto) {
        Site site = siteRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", id));

        if (dto.getName() != null) {
            site.setName(dto.getName());
        }
        if (dto.getAddress() != null) {
            site.setAddress(dto.getAddress());
        }
        if (dto.getIsActive() != null) {
            site.setActive(dto.getIsActive());
        }
        if (dto.getClientId() != null) {
            site.setClientId(dto.getClientId());
        }

        site = siteRepository.save(site);
        log.info("Site updated: id={}", site.getId());
        return toResponseDto(site);
    }

    /**
     * Gets a site by ID within an organization scope.
     *
     * @param id the site UUID
     * @param organizationId the org UUID
     * @return the site response
     */
    @Transactional(readOnly = true)
    public SiteResponseDto getSiteById(UUID id, UUID organizationId) {
        Site site = siteRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", id));
        return toResponseDto(site);
    }

    /**
     * Lists sites in an organization with pagination.
     *
     * @param organizationId the organization UUID
     * @param pageRequest pagination parameters
     * @return paginated site list
     */
    @Transactional(readOnly = true)
    public PaginatedSites listSites(UUID organizationId,
                                    PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest);

        Page<Site> page = siteRepository.findByOrganizationId(
                organizationId, pageable);

        List<SiteResponseDto> sites = page.getContent().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedSites(sites, meta);
    }

    /**
     * Sets the geofence for a site.
     * Supports circular (radius-based) and polygon (GeoJSON) geofences.
     * Invalidates the Redis geofence cache for this site.
     *
     * @param siteId the site UUID
     * @param organizationId the org UUID
     * @param dto the geofence data
     */
    @Transactional
    public void setGeofence(UUID siteId, UUID organizationId, GeofenceDto dto) {
        Site site = siteRepository.findByIdAndOrganizationId(siteId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", siteId));

        if (dto.getGeoJson() != null && !dto.getGeoJson().isBlank()) {
            // Polygon geofence from GeoJSON (Leaflet Draw)
            siteRepository.setPolygonGeofence(siteId, dto.getGeoJson());
            log.info("Polygon geofence set for site: id={}", siteId);
        } else if (dto.getRadiusMeters() != null) {
            // Circular geofence from lat/lng/radius
            siteRepository.setCircularGeofence(
                    siteId, dto.getLatitude(), dto.getLongitude(),
                    dto.getRadiusMeters());
            site.setLatitude(dto.getLatitude());
            site.setLongitude(dto.getLongitude());
            site.setGeofenceRadius(dto.getRadiusMeters());
            siteRepository.save(site);
            log.info("Circular geofence set for site: id={}, radius={}m",
                    siteId, dto.getRadiusMeters());
        }

        // Invalidate geofence cache
        invalidateGeofenceCache(siteId);
    }

    /**
     * Deactivates a site (soft delete).
     *
     * @param siteId the site UUID
     * @param organizationId the org UUID
     */
    @Transactional
    public void deactivateSite(UUID siteId, UUID organizationId) {
        Site site = siteRepository.findByIdAndOrganizationId(siteId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", siteId));
        site.setActive(false);
        siteRepository.save(site);
        log.info("Site deactivated: id={}", siteId);
    }

    /**
     * Invalidates the Redis geofence cache for a site.
     */
    private void invalidateGeofenceCache(UUID siteId) {
        String cacheKey = String.format(GEOFENCE_CACHE_PREFIX, siteId);
        try {
            redisTemplate.delete(cacheKey);
            log.debug("Geofence cache invalidated for site: {}", siteId);
        } catch (Exception ex) {
            log.warn("Failed to invalidate geofence cache for site {}: {}",
                    siteId, ex.getMessage());
        }
    }

    /**
     * Builds pageable from PageRequestDto.
     */
    private PageRequest buildPageable(PageRequestDto pageRequest) {
        Sort sort = Sort.by(
                "ASC".equalsIgnoreCase(pageRequest.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                pageRequest.getSortBy() != null
                        ? pageRequest.getSortBy() : "createdAt");

        return PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                sort);
    }

    /**
     * Maps Site entity to SiteResponseDto.
     */
    private SiteResponseDto toResponseDto(Site site) {
        return SiteResponseDto.builder()
                .id(site.getId())
                .name(site.getName())
                .address(site.getAddress())
                .latitude(site.getLatitude())
                .longitude(site.getLongitude())
                .geofenceRadius(site.getGeofenceRadius())
                .isActive(site.isActive())
                .clientId(site.getClientId())
                .organizationId(site.getOrganizationId())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .build();
    }

    /**
     * Record holding paginated site results.
     */
    public record PaginatedSites(List<SiteResponseDto> sites,
                                 PaginationMeta meta) {
    }
}
