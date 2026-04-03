package com.fm.be.common.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint returning connectivity status for all critical services.
 *
 * <p>Reports: database connectivity, Redis connectivity, uptime, and version.
 * Publicly accessible — no authentication required.</p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "System health and connectivity checks (public)")
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.application.name:fm-be}")
    private String applicationName;

    /**
     * Returns detailed health status including service connectivity.
     *
     * @return health status with uptime, version, and service checks
     */
    @Operation(
            summary = "System health check",
            description = "Returns service status for database, Redis, uptime, and version. No auth required.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Health status retrieved")
            })
    @GetMapping
    public Map<String, Object> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> services = new LinkedHashMap<>();

        boolean dbHealthy = checkDatabase();
        boolean redisHealthy = checkRedis();
        boolean allHealthy = dbHealthy && redisHealthy;

        response.put("status", allHealthy ? "UP" : "DEGRADED");
        response.put("uptime", getUptimeString());
        response.put("version", "1.0.0-SNAPSHOT");

        services.put("database", buildServiceStatus(dbHealthy));
        services.put("redis", buildServiceStatus(redisHealthy));

        response.put("services", services);
        response.put("application", applicationName);

        return response;
    }

    /**
     * Checks PostgreSQL connectivity by executing a simple validation query.
     *
     * @return true if the database is reachable
     */
    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3);
        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Checks Redis connectivity by executing a PING command.
     *
     * @return true if Redis is reachable
     */
    private boolean checkRedis() {
        try {
            String result = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equals(result);
        } catch (Exception ex) {
            log.warn("Redis health check failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Computes the JVM uptime as a human-readable string.
     *
     * @return uptime string (e.g., "2h 15m 30s")
     */
    private String getUptimeString() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Duration duration = Duration.ofMillis(uptimeMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }

    /**
     * Builds a service status map with status string.
     */
    private Map<String, String> buildServiceStatus(boolean healthy) {
        return Map.of("status", healthy ? "UP" : "DOWN");
    }
}
