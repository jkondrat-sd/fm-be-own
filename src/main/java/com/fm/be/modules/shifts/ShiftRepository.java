package com.fm.be.modules.shifts;

import com.fm.be.modules.shifts.entity.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the shifts table.
 */
@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    /**
     * Finds all shifts for a site (paginated).
     */
    Page<Shift> findBySiteId(UUID siteId, Pageable pageable);

    /**
     * Finds all active shifts for a site.
     */
    List<Shift> findBySiteIdAndIsActiveTrue(UUID siteId);
}
