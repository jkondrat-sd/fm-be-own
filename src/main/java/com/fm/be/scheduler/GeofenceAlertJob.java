package com.fm.be.scheduler;

import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.notifications.NotificationService;
import com.fm.be.modules.sites.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Async job for detecting geofence violations.
 *
 * <p>Triggered when a location update is received. Checks if the employee
 * is outside their assigned site's geofence for more than 5 minutes
 * and sends an alert notification if so.</p>
 *
 * <p>Also provides a scheduled scan that runs every 2 minutes to catch
 * any missed real-time checks.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeofenceAlertJob {

    private static final String LOCATION_KEY_PREFIX = "loc:";
    private static final String ALERT_KEY_PREFIX = "geofence:alert:";
    private static final long THRESHOLD_MINUTES = 5;

    private final EmployeeRepository employeeRepository;
    private final SiteRepository siteRepository;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Checks geofence compliance for a single employee after a location update.
     * Called asynchronously from the location update flow.
     *
     * @param employeeId the employee who sent the location update
     */
    @Async("payrollExecutor")
    public void checkGeofence(UUID employeeId) {
        try {
            Optional<Employee> optEmployee = employeeRepository
                    .findById(employeeId);
            if (optEmployee.isEmpty()) {
                return;
            }

            Employee employee = optEmployee.get();
            if (employee.getSiteId() == null) {
                return;
            }

            String locKey = LOCATION_KEY_PREFIX + employeeId;
            Map<Object, Object> fields = redisTemplate.opsForHash()
                    .entries(locKey);
            if (fields.isEmpty()) {
                return;
            }

            double lat = parseDouble(fields.get("lat"));
            double lng = parseDouble(fields.get("lng"));

            boolean inside;
            try {
                inside = siteRepository.isPointInsideGeofence(
                        employee.getSiteId(), lat, lng);
            } catch (Exception e) {
                log.debug("PostGIS unavailable for geofence check: {}",
                        e.getMessage());
                return;
            }

            String alertKey = ALERT_KEY_PREFIX + employeeId;

            if (!inside) {
                handleOutsideGeofence(employee, alertKey);
            } else {
                clearAlert(alertKey);
            }

        } catch (Exception e) {
            log.warn("Geofence check failed for employeeId={}: {}",
                    employeeId, e.getMessage());
        }
    }

    /**
     * Handles an employee detected outside the geofence.
     * If they've been outside for more than 5 minutes, sends an alert.
     */
    private void handleOutsideGeofence(Employee employee, String alertKey) {
        String firstDetected = (String) redisTemplate.opsForValue()
                .get(alertKey);

        if (firstDetected == null) {
            // First detection — record the time
            redisTemplate.opsForValue().set(
                    alertKey, Instant.now().toString(),
                    Duration.ofMinutes(30));
        } else {
            Instant detected = Instant.parse(firstDetected);
            long minutesOutside = Duration.between(detected, Instant.now())
                    .toMinutes();

            if (minutesOutside >= THRESHOLD_MINUTES) {
                try {
                    notificationService.sendToUser(
                            employee.getUserId(),
                            "Geofence Alert",
                            String.format(
                                    "%s has been outside the site boundary "
                                            + "for %d minutes",
                                    employee.getFullName(), minutesOutside),
                            "GEOFENCE_ALERT");

                    // Reset the alert timer to avoid spamming
                    redisTemplate.opsForValue().set(
                            alertKey, Instant.now().toString(),
                            Duration.ofMinutes(30));

                    log.info("Geofence alert sent: employeeId={}, "
                                    + "minutesOutside={}",
                            employee.getId(), minutesOutside);
                } catch (Exception e) {
                    log.warn("Failed to send geofence alert: "
                                    + "employeeId={}",
                            employee.getId(), e);
                }
            }
        }
    }

    /**
     * Clears the geofence alert when employee returns inside the geofence.
     */
    private void clearAlert(String alertKey) {
        Boolean deleted = redisTemplate.delete(alertKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Geofence alert cleared: key={}", alertKey);
        }
    }

    /**
     * Scans all active location keys and checks geofence compliance.
     * Runs every 2 minutes as a catch-all.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 120_000)
    public void scanGeofenceViolations() {
        Set<String> keys = redisTemplate.keys(LOCATION_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        int checked = 0;
        for (String key : keys) {
            try {
                String employeeIdStr = key.substring(
                        LOCATION_KEY_PREFIX.length());
                UUID employeeId = UUID.fromString(employeeIdStr);
                checkGeofence(employeeId);
                checked++;
            } catch (Exception e) {
                log.debug("Skipping geofence scan for key={}: {}",
                        key, e.getMessage());
            }
        }

        if (checked > 0) {
            log.debug("Geofence scan complete: checked={} employees",
                    checked);
        }
    }

    private double parseDouble(Object value) {
        return value != null
                ? Double.parseDouble(String.valueOf(value)) : 0.0;
    }
}
