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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for live location ingestion, batch persistence, and spatial queries.
 *
 * <p>Strategy: each location update is written to Redis only (fast path, &lt;100ms).
 * A scheduled job flushes Redis data to PostgreSQL every 5 minutes. Live location
 * reads are pure Redis operations. Heatmaps query aggregated PostgreSQL data.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private static final String LOCATION_KEY_PREFIX = "loc:";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(120);
    private static final long GEOFENCE_ALERT_THRESHOLD_MINUTES = 5;

    private final LocationRepository locationRepository;
    private final EmployeeRepository employeeRepository;
    private final SiteRepository siteRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Handles a location update from a mobile device. Writes to Redis
     * with a 120-second TTL. If the data is stale (device hasn't sent
     * an update in &gt;120s), the key auto-expires.
     *
     * @param employeeId the employee sending the update
     * @param dto the location data
     */
    public void handleLocationUpdate(UUID employeeId, LocationUpdateDto dto) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException(
                    "Employee", "id", employeeId);
        }

        // Write to Redis as a hash for fast per-field reads
        String key = LOCATION_KEY_PREFIX + employeeId;
        Map<String, String> fields = new HashMap<>();
        fields.put("lat", String.valueOf(dto.getLatitude()));
        fields.put("lng", String.valueOf(dto.getLongitude()));
        fields.put("accuracy", dto.getAccuracy() != null
                ? String.valueOf(dto.getAccuracy()) : "");
        fields.put("battery", dto.getBattery() != null
                ? String.valueOf(dto.getBattery()) : "");
        fields.put("signal", dto.getSignal() != null
                ? String.valueOf(dto.getSignal()) : "");
        fields.put("airplaneMode",
                String.valueOf(dto.isAirplaneMode()));
        fields.put("updatedAt", Instant.now().toString());

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, LOCATION_TTL);

        log.debug("Location updated in Redis: employee={}, lat={}, lng={}",
                employeeId, dto.getLatitude(), dto.getLongitude());
    }

    /**
     * Scheduled job that flushes location data from Redis to PostgreSQL.
     * Runs every 5 minutes. Scans for all {@code loc:*} keys and batch-inserts
     * into the location_history table.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    @Transactional
    public void flushLocationsToDatabase() {
        Set<String> keys = redisTemplate.keys(LOCATION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.debug("No location keys to flush");
            return;
        }

        List<LocationHistory> batch = new ArrayList<>();
        for (String key : keys) {
            try {
                Map<Object, Object> fields = redisTemplate.opsForHash()
                        .entries(key);
                if (fields.isEmpty()) {
                    continue;
                }

                String employeeIdStr = key.substring(
                        LOCATION_KEY_PREFIX.length());
                UUID employeeId = UUID.fromString(employeeIdStr);

                LocationHistory history = LocationHistory.builder()
                        .employeeId(employeeId)
                        .latitude(parseDouble(fields.get("lat")))
                        .longitude(parseDouble(fields.get("lng")))
                        .accuracy(parseDoubleNullable(fields.get("accuracy")))
                        .battery(parseDoubleNullable(fields.get("battery")))
                        .signal(parseDoubleNullable(fields.get("signal")))
                        .airplaneMode(Boolean.parseBoolean(
                                String.valueOf(fields.get("airplaneMode"))))
                        .recordedAt(parseInstant(fields.get("updatedAt")))
                        .build();
                batch.add(history);
            } catch (Exception e) {
                log.warn("Failed to parse location from Redis key={}: {}",
                        key, e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            locationRepository.saveAll(batch);
            log.info("Flushed {} location records from Redis to PostgreSQL",
                    batch.size());
        }
    }

    /**
     * Gets live locations for all employees at a given site.
     * Pure Redis read for fast response.
     *
     * @param siteId the site UUID
     * @return list of live locations for employees at the site
     */
    public List<LiveLocationDto> getLiveLocations(UUID siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ResourceNotFoundException("Site", "id", siteId);
        }

        // Get all employees assigned to this site
        List<Employee> employees = employeeRepository.findBySiteId(siteId);

        return employees.stream()
                .map(emp -> buildLiveLocation(emp))
                .filter(loc -> loc != null)
                .collect(Collectors.toList());
    }

    /**
     * Gets heatmap data from aggregated PostgreSQL location history.
     *
     * @param siteId the site UUID
     * @param start date range start
     * @param end date range end
     * @return heatmap data with weighted GPS points
     */
    @Transactional(readOnly = true)
    public HeatmapDto getHeatmapData(UUID siteId, Instant start, Instant end) {
        if (!siteRepository.existsById(siteId)) {
            throw new ResourceNotFoundException("Site", "id", siteId);
        }

        // Get employees at this site
        List<Employee> employees = employeeRepository.findBySiteId(siteId);
        List<UUID> employeeIds = employees.stream()
                .map(Employee::getId)
                .collect(Collectors.toList());

        // Aggregate location data into grid-based heatmap points
        // Using a simple grid aggregation approach
        Map<String, HeatmapDto.HeatmapPoint> gridMap = new HashMap<>();
        final double gridSize = 0.0005; // ~55m grid cells

        for (UUID empId : employeeIds) {
            List<LocationHistory> locs = locationRepository
                    .findByEmployeeIdAndRecordedAtBetween(empId, start, end);
            for (LocationHistory loc : locs) {
                // Snap to grid
                double gridLat = Math.round(loc.getLatitude() / gridSize)
                        * gridSize;
                double gridLng = Math.round(loc.getLongitude() / gridSize)
                        * gridSize;
                String gridKey = gridLat + "," + gridLng;

                gridMap.computeIfAbsent(gridKey, k ->
                        HeatmapDto.HeatmapPoint.builder()
                                .latitude(gridLat)
                                .longitude(gridLng)
                                .weight(0)
                                .build());
                HeatmapDto.HeatmapPoint point = gridMap.get(gridKey);
                point.setWeight(point.getWeight() + 1);
            }
        }

        return HeatmapDto.builder()
                .points(new ArrayList<>(gridMap.values()))
                .build();
    }

    /**
     * Gets geofence violation alerts — employees outside their site &gt;5 min.
     *
     * @param organizationId the org UUID scope
     * @return list of active geofence alerts
     */
    public List<GeofenceAlertDto> getAlerts(UUID organizationId) {
        // This is a simplified in-memory approach. In production,
        // the GeofenceAlertJob would maintain a Redis set of active alerts.
        List<GeofenceAlertDto> alerts = new ArrayList<>();

        // Check all active employees with live location data
        Set<String> keys = redisTemplate.keys(LOCATION_KEY_PREFIX + "*");
        if (keys == null) {
            return alerts;
        }

        for (String key : keys) {
            try {
                String employeeIdStr = key.substring(
                        LOCATION_KEY_PREFIX.length());
                UUID employeeId = UUID.fromString(employeeIdStr);

                Employee emp = employeeRepository.findById(employeeId)
                        .orElse(null);
                if (emp == null || emp.getSiteId() == null
                        || !emp.getOrganizationId().equals(organizationId)) {
                    continue;
                }

                Map<Object, Object> fields = redisTemplate.opsForHash()
                        .entries(key);
                double lat = parseDouble(fields.get("lat"));
                double lng = parseDouble(fields.get("lng"));

                // Check if inside geofence
                boolean inside;
                try {
                    inside = siteRepository.isPointInsideGeofence(
                            emp.getSiteId(), lat, lng);
                } catch (Exception e) {
                    continue; // Skip if PostGIS unavailable
                }

                if (!inside) {
                    Site site = siteRepository.findById(emp.getSiteId())
                            .orElse(null);
                    Instant updatedAt = parseInstant(fields.get("updatedAt"));
                    long minutesOutside = updatedAt != null
                            ? Duration.between(updatedAt, Instant.now())
                                    .toMinutes()
                            : 0;

                    if (minutesOutside >= GEOFENCE_ALERT_THRESHOLD_MINUTES) {
                        alerts.add(GeofenceAlertDto.builder()
                                .employeeId(employeeId)
                                .employeeName(emp.getFullName())
                                .siteId(emp.getSiteId())
                                .siteName(site != null ? site.getName() : null)
                                .lastLatitude(lat)
                                .lastLongitude(lng)
                                .detectedAt(updatedAt)
                                .minutesOutside(minutesOutside)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("Error checking geofence alert for key={}: {}",
                        key, e.getMessage());
            }
        }

        return alerts;
    }

    /** Builds a LiveLocationDto from Redis data for a given employee. */
    private LiveLocationDto buildLiveLocation(Employee emp) {
        String key = LOCATION_KEY_PREFIX + emp.getId();
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
        if (fields.isEmpty()) {
            return null;
        }

        return LiveLocationDto.builder()
                .employeeId(emp.getId())
                .employeeName(emp.getFullName())
                .latitude(parseDouble(fields.get("lat")))
                .longitude(parseDouble(fields.get("lng")))
                .accuracy(parseDoubleNullable(fields.get("accuracy")))
                .battery(parseDoubleNullable(fields.get("battery")))
                .signal(parseDoubleNullable(fields.get("signal")))
                .airplaneMode(Boolean.parseBoolean(
                        String.valueOf(fields.get("airplaneMode"))))
                .lastUpdated(parseInstant(fields.get("updatedAt")))
                .build();
    }

    private double parseDouble(Object value) {
        return value != null ? Double.parseDouble(String.valueOf(value)) : 0.0;
    }

    private Double parseDoubleNullable(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return null;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Instant parseInstant(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return Instant.now();
        }
        return Instant.parse(String.valueOf(value));
    }
}
