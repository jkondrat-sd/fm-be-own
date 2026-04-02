package com.fm.be.modules.sites;

import com.fm.be.modules.sites.entity.Site;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the sites table.
 * Includes native PostGIS queries for geofence operations.
 */
@Repository
public interface SiteRepository extends JpaRepository<Site, UUID> {

    /**
     * Finds all sites in an organization (paginated).
     *
     * @param organizationId the organization UUID
     * @param pageable pagination parameters
     * @return paginated sites
     */
    Page<Site> findByOrganizationId(UUID organizationId, Pageable pageable);

    /**
     * Finds all active sites in an organization.
     *
     * @param organizationId the organization UUID
     * @param pageable pagination parameters
     * @return paginated active sites
     */
    Page<Site> findByOrganizationIdAndIsActiveTrue(UUID organizationId,
                                                    Pageable pageable);

    /**
     * Finds a site by ID within an organization scope.
     *
     * @param id the site UUID
     * @param organizationId the organization UUID
     * @return the site if found and belongs to the organization
     */
    Optional<Site> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Sets a circular geofence by converting lat/lng/radius to a PostGIS
     * geography polygon using ST_Buffer.
     *
     * <p>This uses a native query because Hibernate Spatial operations
     * require the PostGIS extension at the database level.</p>
     *
     * @param siteId the site UUID
     * @param latitude center latitude
     * @param longitude center longitude
     * @param radiusMeters radius in meters
     */
    @Modifying
    @Query(value = "UPDATE sites SET geofence = ST_Buffer("
            + "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, "
            + ":radiusMeters), "
            + "latitude = :latitude, longitude = :longitude, "
            + "geofence_radius = :radiusMeters, "
            + "updated_at = NOW() "
            + "WHERE id = :siteId",
            nativeQuery = true)
    void setCircularGeofence(UUID siteId, double latitude, double longitude,
                             double radiusMeters);

    /**
     * Sets a polygon geofence from GeoJSON input.
     *
     * @param siteId the site UUID
     * @param geoJson the GeoJSON polygon string
     */
    @Modifying
    @Query(value = "UPDATE sites SET geofence = ST_GeomFromGeoJSON(:geoJson)::geography, "
            + "updated_at = NOW() "
            + "WHERE id = :siteId",
            nativeQuery = true)
    void setPolygonGeofence(UUID siteId, String geoJson);

    /**
     * Checks if a point is within a site's geofence.
     *
     * @param siteId the site UUID
     * @param latitude the point latitude
     * @param longitude the point longitude
     * @return true if the point is within the geofence
     */
    @Query(value = "SELECT CASE WHEN geofence IS NOT NULL "
            + "THEN ST_DWithin(geofence, "
            + "ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, 0) "
            + "ELSE false END "
            + "FROM sites WHERE id = :siteId",
            nativeQuery = true)
    boolean isPointInsideGeofence(UUID siteId, double latitude, double longitude);
}
