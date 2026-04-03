package com.fm.be.modules.location;

import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.location.dto.HeatmapDto;
import com.fm.be.modules.location.dto.LiveLocationDto;
import com.fm.be.modules.location.dto.LocationUpdateDto;
import com.fm.be.modules.location.entity.LocationHistory;
import com.fm.be.modules.sites.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LocationService}.
 * Covers Redis write, batch flush, live location read, and heatmap.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private LocationService locationService;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // StringRedisTemplate mock setup for hash ops
    }

    @Nested
    @DisplayName("handleLocationUpdate")
    class LocationUpdateTests {

        @Test
        @DisplayName("should write location to Redis")
        void handleLocationUpdate_writesToRedis() {
            when(employeeRepository.existsById(employeeId)).thenReturn(true);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            LocationUpdateDto dto = LocationUpdateDto.builder()
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .accuracy(10.0)
                    .battery(0.85)
                    .build();

            locationService.handleLocationUpdate(employeeId, dto);

            verify(hashOperations).putAll(
                    eq("loc:" + employeeId), any(Map.class));
            verify(redisTemplate).expire(
                    eq("loc:" + employeeId), any(Duration.class));
        }

        @Test
        @DisplayName("should throw when employee not found")
        void handleLocationUpdate_notFound_throws() {
            when(employeeRepository.existsById(employeeId)).thenReturn(false);

            LocationUpdateDto dto = LocationUpdateDto.builder()
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .build();

            assertThatThrownBy(() ->
                    locationService.handleLocationUpdate(employeeId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("flushLocationsToDatabase")
    class FlushTests {

        @Test
        @DisplayName("should flush Redis keys to PostgreSQL")
        void flushLocationsToDatabase_savesAll() {
            String key = "loc:" + employeeId;
            when(redisTemplate.keys("loc:*")).thenReturn(Set.of(key));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Map<Object, Object> fields = new HashMap<>();
            fields.put("lat", "19.076");
            fields.put("lng", "72.878");
            fields.put("accuracy", "10.0");
            fields.put("battery", "0.85");
            fields.put("signal", "");
            fields.put("airplaneMode", "false");
            fields.put("updatedAt", Instant.now().toString());
            when(hashOperations.entries(key)).thenReturn(fields);

            locationService.flushLocationsToDatabase();

            verify(locationRepository).saveAll(any(List.class));
        }

        @Test
        @DisplayName("should handle empty keys gracefully")
        void flushLocationsToDatabase_noKeys_noop() {
            when(redisTemplate.keys("loc:*")).thenReturn(Set.of());
            locationService.flushLocationsToDatabase();
            // No exception, no save call
        }
    }

    @Nested
    @DisplayName("getLiveLocations")
    class LiveLocationTests {

        @Test
        @DisplayName("should return live locations from Redis")
        void getLiveLocations_returnsFromRedis() {
            when(siteRepository.existsById(siteId)).thenReturn(true);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .fullName("John Doe")
                    .siteId(siteId)
                    .build();
            when(employeeRepository.findBySiteId(siteId))
                    .thenReturn(List.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Map<Object, Object> fields = new HashMap<>();
            fields.put("lat", "19.076");
            fields.put("lng", "72.878");
            fields.put("accuracy", "10.0");
            fields.put("battery", "0.85");
            fields.put("signal", "");
            fields.put("airplaneMode", "false");
            fields.put("updatedAt", Instant.now().toString());
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(fields);

            List<LiveLocationDto> result =
                    locationService.getLiveLocations(siteId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
            assertThat(result.get(0).getLatitude()).isEqualTo(19.076);
        }

        @Test
        @DisplayName("should throw when site not found")
        void getLiveLocations_siteNotFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.existsById(randomId)).thenReturn(false);

            assertThatThrownBy(() ->
                    locationService.getLiveLocations(randomId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getHeatmapData")
    class HeatmapTests {

        @Test
        @DisplayName("should aggregate location data into weighted grid")
        void getHeatmapData_aggregatesIntoGrid() {
            when(siteRepository.existsById(siteId)).thenReturn(true);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .fullName("Jane Doe")
                    .siteId(siteId)
                    .build();
            when(employeeRepository.findBySiteId(siteId))
                    .thenReturn(List.of(emp));

            LocationHistory loc1 = LocationHistory.builder()
                    .latitude(19.076)
                    .longitude(72.878)
                    .build();
            LocationHistory loc2 = LocationHistory.builder()
                    .latitude(19.076)
                    .longitude(72.878)
                    .build();
            when(locationRepository.findByEmployeeIdAndRecordedAtBetween(
                    eq(employeeId), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(loc1, loc2));

            Instant start = Instant.now().minusSeconds(86400);
            Instant end = Instant.now();

            HeatmapDto result = locationService.getHeatmapData(
                    siteId, start, end);

            assertThat(result.getPoints()).isNotEmpty();
            // Same grid cell, so weight should be 2
            assertThat(result.getPoints().get(0).getWeight()).isEqualTo(2);
        }
    }
}
