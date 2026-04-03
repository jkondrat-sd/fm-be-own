package com.fm.be.scheduler;

import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.notifications.NotificationService;
import com.fm.be.modules.sites.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeofenceAlertJob}.
 */
@ExtendWith(MockitoExtension.class)
class GeofenceAlertJobTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private GeofenceAlertJob geofenceAlertJob;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @Nested
    @DisplayName("checkGeofence")
    class CheckGeofenceTests {

        @Test
        @DisplayName("should skip when employee not found")
        void checkGeofence_employeeNotFound_skips() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.empty());

            geofenceAlertJob.checkGeofence(employeeId);

            verify(notificationService, never()).sendToUser(
                    any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should skip when employee has no site")
        void checkGeofence_noSite_skips() {
            Employee emp = Employee.builder()
                    .id(employeeId)
                    .userId(userId)
                    .siteId(null)
                    .build();

            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));

            geofenceAlertJob.checkGeofence(employeeId);

            verify(redisTemplate, never()).opsForHash();
        }

        @Test
        @DisplayName("should skip when no location data in Redis")
        void checkGeofence_noLocationData_skips() {
            Employee emp = Employee.builder()
                    .id(employeeId)
                    .userId(userId)
                    .siteId(siteId)
                    .build();

            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(Collections.emptyMap());

            geofenceAlertJob.checkGeofence(employeeId);

            verify(siteRepository, never()).isPointInsideGeofence(
                    any(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("should clear alert when employee is inside geofence")
        void checkGeofence_insideGeofence_clearsAlert() {
            Employee emp = Employee.builder()
                    .id(employeeId)
                    .userId(userId)
                    .siteId(siteId)
                    .build();

            Map<Object, Object> locFields = new HashMap<>();
            locFields.put("lat", "19.076");
            locFields.put("lng", "72.877");

            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(locFields);
            when(siteRepository.isPointInsideGeofence(siteId, 19.076, 72.877))
                    .thenReturn(true);

            geofenceAlertJob.checkGeofence(employeeId);

            verify(redisTemplate).delete("geofence:alert:" + employeeId);
        }

        @Test
        @DisplayName("should record first detection when outside geofence")
        void checkGeofence_firstOutside_recordsTime() {
            Employee emp = Employee.builder()
                    .id(employeeId)
                    .userId(userId)
                    .siteId(siteId)
                    .fullName("Test Employee")
                    .build();

            Map<Object, Object> locFields = new HashMap<>();
            locFields.put("lat", "19.100");
            locFields.put("lng", "72.900");

            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(locFields);
            when(siteRepository.isPointInsideGeofence(siteId, 19.1, 72.9))
                    .thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("geofence:alert:" + employeeId))
                    .thenReturn(null);

            geofenceAlertJob.checkGeofence(employeeId);

            verify(valueOperations).set(
                    eq("geofence:alert:" + employeeId),
                    anyString(), any());
            verify(notificationService, never()).sendToUser(
                    any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should send alert when outside geofence > 5 minutes")
        void checkGeofence_outsideLongEnough_sendsAlert() {
            Employee emp = Employee.builder()
                    .id(employeeId)
                    .userId(userId)
                    .siteId(siteId)
                    .fullName("Test Employee")
                    .build();

            Map<Object, Object> locFields = new HashMap<>();
            locFields.put("lat", "19.100");
            locFields.put("lng", "72.900");

            // First detected 10 minutes ago
            String tenMinutesAgo = Instant.now()
                    .minusSeconds(600).toString();

            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(emp));
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("loc:" + employeeId))
                    .thenReturn(locFields);
            when(siteRepository.isPointInsideGeofence(siteId, 19.1, 72.9))
                    .thenReturn(false);
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("geofence:alert:" + employeeId))
                    .thenReturn(tenMinutesAgo);

            geofenceAlertJob.checkGeofence(employeeId);

            verify(notificationService).sendToUser(
                    eq(userId),
                    eq("Geofence Alert"),
                    anyString(),
                    eq("GEOFENCE_ALERT"));
        }
    }

    @Nested
    @DisplayName("scanGeofenceViolations")
    class ScanTests {

        @Test
        @DisplayName("should scan all location keys")
        void scanGeofenceViolations_scansAllKeys() {
            when(redisTemplate.keys("loc:*"))
                    .thenReturn(Set.of("loc:" + employeeId));
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.empty());

            geofenceAlertJob.scanGeofenceViolations();

            verify(employeeRepository).findById(employeeId);
        }

        @Test
        @DisplayName("should handle null keys gracefully")
        void scanGeofenceViolations_nullKeys_doesNothing() {
            when(redisTemplate.keys("loc:*")).thenReturn(null);

            geofenceAlertJob.scanGeofenceViolations();

            verify(employeeRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should handle empty keys gracefully")
        void scanGeofenceViolations_emptyKeys_doesNothing() {
            when(redisTemplate.keys("loc:*"))
                    .thenReturn(Collections.emptySet());

            geofenceAlertJob.scanGeofenceViolations();

            verify(employeeRepository, never()).findById(any());
        }
    }
}
