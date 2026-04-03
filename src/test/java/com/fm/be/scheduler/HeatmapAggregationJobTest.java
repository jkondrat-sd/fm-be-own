package com.fm.be.scheduler;

import com.fm.be.modules.location.LocationRepository;
import com.fm.be.modules.location.entity.LocationHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HeatmapAggregationJob}.
 */
@ExtendWith(MockitoExtension.class)
class HeatmapAggregationJobTest {

    @Mock
    private LocationRepository locationRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private HeatmapAggregationJob heatmapAggregationJob;

    @Test
    @DisplayName("should aggregate location data into grid cells")
    void aggregateHeatmap_withData_aggregatesIntoGrid() {
        LocationHistory loc1 = LocationHistory.builder()
                .id(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .latitude(19.0760)
                .longitude(72.8777)
                .recordedAt(Instant.now())
                .build();
        LocationHistory loc2 = LocationHistory.builder()
                .id(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .latitude(19.0760)
                .longitude(72.8777)
                .recordedAt(Instant.now())
                .build();

        when(locationRepository.findByRecordedAtBetween(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(loc1, loc2));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        heatmapAggregationJob.aggregateHeatmap();

        verify(hashOperations).putAll(anyString(), any(Map.class));
        verify(redisTemplate).expire(anyString(), any());
    }

    @Test
    @DisplayName("should skip aggregation when no data exists")
    void aggregateHeatmap_noData_skips() {
        when(locationRepository.findByRecordedAtBetween(
                any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        heatmapAggregationJob.aggregateHeatmap();

        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    @DisplayName("should handle locations at different grid positions")
    void aggregateHeatmap_differentPositions_createsMultipleCells() {
        LocationHistory loc1 = LocationHistory.builder()
                .id(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .latitude(19.0760)
                .longitude(72.8777)
                .recordedAt(Instant.now())
                .build();
        LocationHistory loc2 = LocationHistory.builder()
                .id(UUID.randomUUID())
                .employeeId(UUID.randomUUID())
                .latitude(28.6139)
                .longitude(77.2090)
                .recordedAt(Instant.now())
                .build();

        when(locationRepository.findByRecordedAtBetween(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(loc1, loc2));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        heatmapAggregationJob.aggregateHeatmap();

        verify(hashOperations).putAll(anyString(), any(Map.class));
    }
}
