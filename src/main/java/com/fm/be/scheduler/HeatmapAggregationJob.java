package com.fm.be.scheduler;

import com.fm.be.modules.location.LocationRepository;
import com.fm.be.modules.location.entity.LocationHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled job for heatmap data aggregation.
 *
 * <p>Runs daily at 3:00 AM IST. Processes the previous day's
 * location data into grid-based aggregations stored in Redis
 * for fast heatmap rendering.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeatmapAggregationJob {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final double GRID_SIZE = 0.0005; // ~55m grid cells
    private static final String HEATMAP_KEY_PREFIX = "heatmap:";
    private static final Duration HEATMAP_TTL = Duration.ofDays(365);

    private final LocationRepository locationRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Aggregates the previous day's location data into grid-based
     * heatmap points and stores them in Redis.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    @Transactional(readOnly = true)
    public void aggregateHeatmap() {
        log.info("Starting heatmap aggregation job");
        long startTime = System.currentTimeMillis();

        LocalDate yesterday = LocalDate.now(IST).minusDays(1);
        Instant dayStart = yesterday.atStartOfDay(IST).toInstant();
        Instant dayEnd = yesterday.plusDays(1).atStartOfDay(IST).toInstant();

        List<LocationHistory> locations =
                locationRepository.findByRecordedAtBetween(dayStart, dayEnd);

        if (locations.isEmpty()) {
            log.info("No location data to aggregate for {}", yesterday);
            return;
        }

        Map<String, Integer> grid = new HashMap<>();
        for (LocationHistory loc : locations) {
            double gridLat = Math.round(loc.getLatitude() / GRID_SIZE) * GRID_SIZE;
            double gridLng = Math.round(loc.getLongitude() / GRID_SIZE) * GRID_SIZE;
            String gridKey = gridLat + "," + gridLng;
            grid.merge(gridKey, 1, Integer::sum);
        }

        String redisKey = HEATMAP_KEY_PREFIX + yesterday;
        Map<String, String> redisEntries = new HashMap<>();
        grid.forEach((k, v) -> redisEntries.put(k, String.valueOf(v)));

        redisTemplate.opsForHash().putAll(redisKey, redisEntries);
        redisTemplate.expire(redisKey, HEATMAP_TTL);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Heatmap aggregation complete: date={}, locations={}, "
                        + "gridCells={}, duration={}ms",
                yesterday, locations.size(), grid.size(), elapsed);
    }
}
