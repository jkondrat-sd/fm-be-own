package com.fm.be.modules.location;

import com.fm.be.modules.location.entity.LocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the location_history table.
 * Includes queries for heatmap aggregation and data retention cleanup.
 */
@Repository
public interface LocationRepository extends JpaRepository<LocationHistory, UUID> {

    /**
     * Finds location records for an employee within a time range.
     *
     * @param employeeId the employee UUID
     * @param start range start
     * @param end range end
     * @return matching location records
     */
    List<LocationHistory> findByEmployeeIdAndRecordedAtBetween(
            UUID employeeId, Instant start, Instant end);

    /**
     * Deletes location records older than the given timestamp.
     * Used by the 30-day retention cleanup job.
     *
     * @param before delete records recorded before this instant
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM LocationHistory l WHERE l.recordedAt < :before")
    int deleteByRecordedAtBefore(Instant before);

    /**
     * Finds all location records within a time range, typically
     * used for heatmap aggregation queries.
     *
     * @param start range start
     * @param end range end
     * @return matching location records
     */
    List<LocationHistory> findByRecordedAtBetween(Instant start, Instant end);
}
