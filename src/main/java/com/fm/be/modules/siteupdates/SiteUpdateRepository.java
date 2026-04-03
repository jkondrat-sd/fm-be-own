package com.fm.be.modules.siteupdates;

import com.fm.be.modules.siteupdates.entity.SiteUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for the site_updates table.
 * Provides lookups by site, employee, and date range.
 */
@Repository
public interface SiteUpdateRepository extends JpaRepository<SiteUpdate, UUID> {

    /**
     * Finds updates for a site within a date range (paginated).
     */
    Page<SiteUpdate> findBySiteIdAndRecordedAtBetween(
            UUID siteId, Instant start, Instant end, Pageable pageable);

    /**
     * Finds all updates for a site (paginated).
     */
    Page<SiteUpdate> findBySiteId(UUID siteId, Pageable pageable);

    /**
     * Finds updates by a specific employee (paginated).
     */
    Page<SiteUpdate> findByEmployeeId(UUID employeeId, Pageable pageable);
}
