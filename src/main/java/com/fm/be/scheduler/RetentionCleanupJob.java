package com.fm.be.scheduler;

import com.fm.be.modules.location.LocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled job for data retention cleanup.
 *
 * <p>Runs daily at 2:00 AM IST. Deletes location_history records
 * older than 30 days to comply with data retention policy.
 * Media cleanup from R2/S3 would also run here in production.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionCleanupJob {

    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    private final LocationRepository locationRepository;

    /**
     * Deletes location history records older than 30 days.
     * Runs daily at 2:00 AM IST (Asia/Kolkata).
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupLocationData() {
        log.info("Starting location data retention cleanup");
        long startTime = System.currentTimeMillis();

        Instant cutoff = Instant.now().minus(RETENTION_PERIOD);
        int deleted = locationRepository.deleteByRecordedAtBefore(cutoff);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Location cleanup complete: deleted={} records, "
                + "cutoff={}, duration={}ms", deleted, cutoff, elapsed);
    }

    /**
     * Placeholder for media cleanup from R2/S3.
     * Runs daily at 2:30 AM IST. In production this would scan for
     * expired site update media files and delete them from storage.
     */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    public void cleanupExpiredMedia() {
        log.info("Media cleanup job triggered (no-op in V1)");
    }
}
