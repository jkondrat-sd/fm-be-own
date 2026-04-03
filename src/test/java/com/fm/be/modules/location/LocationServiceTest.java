package com.fm.be.modules.location;

import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.location.dto.GeofenceAlertDto;
import com.fm.be.modules.location.dto.HeatmapDto;
import com.fm.be.modules.location.dto.LiveLocationDto;
import com.fm.be.modules.location.dto.LocationUpdateDto;
import com.fm.be.modules.location.entity.LocationHistory;
import com.fm.be.modules.sites.SiteRepository;
import com.fm.be.modules.sites.entity.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
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
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
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
            Cursor<String> cursor = mockCursor(Collections.emptyList());
            when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
            locationService.flushLocationsToDatabase();
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

    @Nested
    @DisplayName("getAlerts")
    class GetAlertsTests {

        private final UUID orgId = UUID.randomUUID();

        private Map<Object, Object> buildLocationFields(
                double lat, double lng, Instant updatedAt) {
            Map<Object, Object> fields = new HashMap<>();
            fields.put("lat", String.valueOf(lat));
            fields.put("lng", String.valueOf(lng));
            fields.put("accuracy", "10.0");
            fields.put("battery", "0.85");
            fields.put("signal", "");
            fields.put("airplaneMode", "false");
            fields.put("updatedAt", updatedAt.toString());
            return fields;
        }

        @Test
        @DisplayName("should return alert when employee is outside geofence > 5 min")
        void getAlerts_outsideGeofence_returnsAlert() {
            String key = "loc:" + employeeId;
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .fullName("John Doe")
                    .siteId(siteId)
                    .organizationId(orgId)
                    .build();
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));

            Instant tenMinutesAgo = Instant.now().minus(
                    Duration.ofMinutes(10));
            when(hashOperations.entries(key))
                    .thenReturn(buildLocationFields(19.0, 72.8, tenMinutesAgo));

            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), anyDouble(), anyDouble()))
                    .thenReturn(false);

            Site site = Site.builder()
                    .id(siteId).name("Main Office").build();
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(site));

            List<GeofenceAlertDto> alerts =
                    locationService.getAlerts(orgId);

            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getEmployeeName()).isEqualTo("John Doe");
            assertThat(alerts.get(0).getSiteName()).isEqualTo("Main Office");
            assertThat(alerts.get(0).getMinutesOutside())
                    .isGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("should not return alert when employee is inside geofence")
        void getAlerts_insideGeofence_noAlert() {
            String key = "loc:" + employeeId;
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .fullName("Jane Doe")
                    .siteId(siteId)
                    .organizationId(orgId)
                    .build();
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));

            when(hashOperations.entries(key))
                    .thenReturn(buildLocationFields(
                            19.0, 72.8, Instant.now()));

            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), anyDouble(), anyDouble()))
                    .thenReturn(true);

            List<GeofenceAlertDto> alerts =
                    locationService.getAlerts(orgId);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("should skip employee from different organization")
        void getAlerts_differentOrg_skipsEmployee() {
            String key = "loc:" + employeeId;
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .siteId(siteId)
                    .organizationId(UUID.randomUUID())
                    .build();
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));

            List<GeofenceAlertDto> alerts =
                    locationService.getAlerts(orgId);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("should return empty when no location keys")
        void getAlerts_noKeys_returnsEmpty() {
            Cursor<String> cursor = mockCursor(Collections.emptyList());
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);

            List<GeofenceAlertDto> alerts =
                    locationService.getAlerts(orgId);

            assertThat(alerts).isEmpty();
        }

        @Test
        @DisplayName("should skip employee with no site assigned")
        void getAlerts_noSiteAssigned_skipsEmployee() {
            String key = "loc:" + employeeId;
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .siteId(null)
                    .organizationId(orgId)
                    .build();
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));

            List<GeofenceAlertDto> alerts =
                    locationService.getAlerts(orgId);

            assertThat(alerts).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLiveLocations - edge cases")
    class LiveLocationEdgeCaseTests {

        @Test
        @DisplayName("should filter out employees with no Redis data")
        void getLiveLocations_noRedisData_filtersOut() {
            when(siteRepository.existsById(siteId)).thenReturn(true);

            Employee emp = Employee.builder()
                    .id(employeeId)
                    .fullName("Ghost Employee")
                    .siteId(siteId)
                    .build();
            when(employeeRepository.findBySiteId(siteId))
                    .thenReturn(List.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(Collections.emptyMap());

            List<LiveLocationDto> result =
                    locationService.getLiveLocations(siteId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleLocationUpdate - edge cases")
    class LocationUpdateEdgeCaseTests {

        @Test
        @DisplayName("should handle null optional fields")
        void handleLocationUpdate_nullOptionals_writesEmptyStrings() {
            when(employeeRepository.existsById(employeeId)).thenReturn(true);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            LocationUpdateDto dto = LocationUpdateDto.builder()
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .accuracy(null)
                    .battery(null)
                    .signal(null)
                    .airplaneMode(true)
                    .build();

            locationService.handleLocationUpdate(employeeId, dto);

            verify(hashOperations).putAll(
                    eq("loc:" + employeeId), any(Map.class));
        }
    }

    @Nested
    @DisplayName("flushLocationsToDatabase - edge cases")
    class FlushEdgeCaseTests {

        @Test
        @DisplayName("should skip keys with empty hash entries")
        void flushLocationsToDatabase_emptyFields_skips() {
            String key = "loc:" + employeeId;
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(key))
                    .thenReturn(Collections.emptyMap());

            locationService.flushLocationsToDatabase();
            // No saveAll call expected since all entries were empty
        }

        @Test
        @DisplayName("should handle malformed Redis data gracefully")
        void flushLocationsToDatabase_malformedData_skips() {
            String key = "loc:not-a-uuid";
            Cursor<String> cursor = mockCursor(List.of(key));
            when(redisTemplate.scan(any(ScanOptions.class)))
                    .thenReturn(cursor);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            Map<Object, Object> badFields = new HashMap<>();
            badFields.put("lat", "not-a-number");
            when(hashOperations.entries(key)).thenReturn(badFields);

            locationService.flushLocationsToDatabase();
            // Should not throw — malformed data is logged and skipped
        }
    }

    @SuppressWarnings("unchecked")
    private static Cursor<String> mockCursor(List<String> items) {
        Iterator<String> iter = items.iterator();
        Cursor<String> cursor = org.mockito.Mockito.mock(Cursor.class,
                org.mockito.Mockito.withSettings().lenient());
        when(cursor.hasNext()).thenAnswer(inv -> iter.hasNext());
        when(cursor.next()).thenAnswer(inv -> iter.next());
        return cursor;
    }
}
