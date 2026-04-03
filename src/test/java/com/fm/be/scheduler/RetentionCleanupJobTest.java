package com.fm.be.scheduler;

import com.fm.be.modules.location.LocationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RetentionCleanupJob}.
 */
@ExtendWith(MockitoExtension.class)
class RetentionCleanupJobTest {

    @Mock
    private LocationRepository locationRepository;

    @InjectMocks
    private RetentionCleanupJob retentionCleanupJob;

    @Test
    @DisplayName("should delete location records older than 30 days")
    void cleanupLocationData_deletesOldRecords() {
        when(locationRepository.deleteByRecordedAtBefore(any(Instant.class)))
                .thenReturn(500);

        retentionCleanupJob.cleanupLocationData();

        verify(locationRepository).deleteByRecordedAtBefore(any(Instant.class));
    }

    @Test
    @DisplayName("should handle zero records gracefully")
    void cleanupLocationData_noRecords_succeeds() {
        when(locationRepository.deleteByRecordedAtBefore(any(Instant.class)))
                .thenReturn(0);

        retentionCleanupJob.cleanupLocationData();

        verify(locationRepository).deleteByRecordedAtBefore(any(Instant.class));
    }

    @Test
    @DisplayName("cleanupExpiredMedia should execute without error")
    void cleanupExpiredMedia_noOp() {
        retentionCleanupJob.cleanupExpiredMedia();
        // No-op in V1, just verify it doesn't throw
    }
}
