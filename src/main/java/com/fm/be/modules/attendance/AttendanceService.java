package com.fm.be.modules.attendance;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.constants.PunchType;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.attendance.dto.AttendanceLogDto;
import com.fm.be.modules.attendance.dto.AttendanceReportDto;
import com.fm.be.modules.attendance.dto.PunchRequestDto;
import com.fm.be.modules.attendance.dto.PunchResponseDto;
import com.fm.be.modules.attendance.entity.AttendanceLog;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.sites.SiteRepository;
import com.fm.be.modules.sites.entity.Site;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for attendance punch operations and reports.
 *
 * <p>Punch flow validates: geofence (via PostGIS), face match score,
 * and idempotent dedup (5-min window). Reports serve the ops dashboard
 * and client portal.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final double FACE_MATCH_THRESHOLD = 0.85;
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final SiteRepository siteRepository;

    /**
     * Records an attendance punch after validating geofence, face match,
     * and idempotent deduplication.
     *
     * @param employeeId the employee punching in/out
     * @param dto the punch request data
     * @return the punch response
     * @throws BusinessRuleException on geofence violation, face mismatch, or duplicate
     */
    @Transactional
    public PunchResponseDto punch(UUID employeeId, PunchRequestDto dto) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "id", employeeId));

        UUID siteId = employee.getSiteId();
        if (siteId == null) {
            throw new BusinessRuleException(ErrorCode.SITE_INACTIVE,
                    "Employee is not assigned to any site");
        }

        // Verify site is active
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", siteId));
        if (!site.isActive()) {
            throw new BusinessRuleException(ErrorCode.SITE_INACTIVE);
        }

        // Check geofence compliance via PostGIS
        boolean insideGeofence = checkGeofence(siteId,
                dto.getLatitude(), dto.getLongitude());
        if (!insideGeofence) {
            throw new BusinessRuleException(ErrorCode.GEOFENCE_VIOLATION,
                    "Employee is outside designated site boundary",
                    Map.of("distance_meters", 0));
        }

        // Validate face match score
        if (dto.getFaceMatchScore() != null
                && dto.getFaceMatchScore() < FACE_MATCH_THRESHOLD) {
            throw new BusinessRuleException(ErrorCode.FACE_MISMATCH,
                    "Face match score below threshold: "
                            + dto.getFaceMatchScore());
        }

        // Idempotent dedup check — reject duplicate punch within 5-min window
        Instant dedupCutoff = Instant.now().minus(DEDUP_WINDOW);
        Optional<AttendanceLog> duplicate = attendanceRepository
                .findRecentDuplicate(employeeId, dto.getPunchType(), dedupCutoff);
        if (duplicate.isPresent()) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_PUNCH,
                    "Duplicate punch within 5-minute window");
        }

        // Record the punch
        AttendanceLog attendanceLog = AttendanceLog.builder()
                .employeeId(employeeId)
                .punchType(dto.getPunchType())
                .punchTime(dto.getTimestamp() != null
                        ? dto.getTimestamp() : Instant.now())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .accuracy(dto.getAccuracy())
                .faceMatchScore(dto.getFaceMatchScore())
                .isInsideGeofence(true)
                .deviceInfo(dto.getDeviceInfo())
                .siteId(siteId)
                .build();

        attendanceLog = attendanceRepository.save(attendanceLog);

        log.info("Attendance punch recorded: employee={}, type={}, site={}",
                employeeId, dto.getPunchType(), siteId);

        return toPunchResponse(attendanceLog);
    }

    /**
     * Gets today's attendance logs for an employee.
     *
     * @param employeeId the employee UUID
     * @return list of today's punch logs
     */
    @Transactional(readOnly = true)
    public List<AttendanceLogDto> getTodayAttendance(UUID employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }

        ZonedDateTime now = ZonedDateTime.now(IST);
        Instant dayStart = now.toLocalDate().atStartOfDay(IST).toInstant();
        Instant dayEnd = now.toLocalDate().plusDays(1)
                .atStartOfDay(IST).toInstant();

        List<AttendanceLog> logs = attendanceRepository
                .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                        employeeId, dayStart, dayEnd);

        return logs.stream()
                .map(this::toLogDto)
                .collect(Collectors.toList());
    }

    /**
     * Gets paginated attendance logs with filters.
     *
     * @param siteId optional site filter
     * @param employeeId optional employee filter
     * @param start date range start
     * @param end date range end
     * @param pageRequest pagination params
     * @return paginated attendance logs
     */
    @Transactional(readOnly = true)
    public PaginatedAttendanceLogs getAttendanceLogs(
            UUID siteId, UUID employeeId,
            Instant start, Instant end,
            PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest, "punchTime");

        Page<AttendanceLog> page;
        if (employeeId != null) {
            page = attendanceRepository.findByEmployeeIdAndPunchTimeBetween(
                    employeeId, start, end, pageable);
        } else if (siteId != null) {
            page = attendanceRepository.findBySiteIdAndPunchTimeBetween(
                    siteId, start, end, pageable);
        } else {
            page = attendanceRepository.findAll(pageable);
        }

        List<AttendanceLogDto> logs = page.getContent().stream()
                .map(this::toLogDto)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedAttendanceLogs(logs, meta);
    }

    /**
     * Generates an attendance report for a site within a date range.
     *
     * @param siteId the site UUID
     * @param start period start
     * @param end period end
     * @return aggregated attendance report
     */
    @Transactional(readOnly = true)
    public AttendanceReportDto getAttendanceReport(UUID siteId,
                                                    Instant start,
                                                    Instant end) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site", "id", siteId));

        long punchIns = attendanceRepository
                .countBySiteIdAndPunchTypeAndPunchTimeBetween(
                        siteId, PunchType.IN, start, end);
        long punchOuts = attendanceRepository
                .countBySiteIdAndPunchTypeAndPunchTimeBetween(
                        siteId, PunchType.OUT, start, end);

        // Calculate number of days in the period for average
        long days = Duration.between(start, end).toDays();
        double avgPunchInsPerDay = days > 0
                ? (double) punchIns / days : punchIns;

        long totalEmployees = employeeRepository.countBySiteId(siteId);

        return AttendanceReportDto.builder()
                .siteId(siteId)
                .siteName(site.getName())
                .periodStart(start)
                .periodEnd(end)
                .totalPunchIns(punchIns)
                .totalPunchOuts(punchOuts)
                .totalEmployees(totalEmployees)
                .averagePunchInsPerDay(avgPunchInsPerDay)
                .build();
    }

    /**
     * Gets attendance summary for a site today.
     *
     * @param siteId the site UUID
     * @return list of today's attendance logs for the site
     */
    @Transactional(readOnly = true)
    public List<AttendanceLogDto> getSiteAttendance(UUID siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ResourceNotFoundException("Site", "id", siteId);
        }

        ZonedDateTime now = ZonedDateTime.now(IST);
        Instant dayStart = now.toLocalDate().atStartOfDay(IST).toInstant();
        Instant dayEnd = now.toLocalDate().plusDays(1)
                .atStartOfDay(IST).toInstant();

        return attendanceRepository
                .findBySiteIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                        siteId, dayStart, dayEnd)
                .stream()
                .map(this::toLogDto)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a point is within a site's geofence using PostGIS.
     * Falls back to Haversine distance check for circular geofences
     * when PostGIS is unavailable.
     */
    private boolean checkGeofence(UUID siteId, double lat, double lng) {
        try {
            return siteRepository.isPointInsideGeofence(siteId, lat, lng);
        } catch (Exception e) {
            // Fallback: use Haversine for circular geofence when PostGIS
            // is unavailable (e.g., in tests without PostGIS container)
            log.warn("PostGIS geofence check failed, using Haversine fallback: {}",
                    e.getMessage());
            return haversineFallback(siteId, lat, lng);
        }
    }

    /**
     * Haversine fallback for circular geofence checks when PostGIS
     * is not available. Uses the site's lat/lng/radius.
     */
    private boolean haversineFallback(UUID siteId, double lat, double lng) {
        Site site = siteRepository.findById(siteId).orElse(null);
        if (site == null || site.getLatitude() == null
                || site.getLongitude() == null
                || site.getGeofenceRadius() == null) {
            return false;
        }

        double distance = haversineDistance(
                site.getLatitude(), site.getLongitude(), lat, lng);
        return distance <= site.getGeofenceRadius();
    }

    /**
     * Calculates Haversine distance between two GPS points in meters.
     */
    private double haversineDistance(double lat1, double lon1,
                                     double lat2, double lon2) {
        final double earthRadius = 6_371_000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    /** Builds a Spring pageable from the shared PageRequestDto. */
    private PageRequest buildPageable(PageRequestDto pageRequest,
                                       String defaultSort) {
        Sort sort = Sort.by(
                "ASC".equalsIgnoreCase(pageRequest.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                pageRequest.getSortBy() != null
                        ? pageRequest.getSortBy() : defaultSort);
        return PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                sort);
    }

    /** Maps AttendanceLog entity to PunchResponseDto. */
    private PunchResponseDto toPunchResponse(AttendanceLog log) {
        return PunchResponseDto.builder()
                .id(log.getId())
                .employeeId(log.getEmployeeId())
                .punchType(log.getPunchType())
                .punchTime(log.getPunchTime())
                .insideGeofence(log.isInsideGeofence())
                .faceMatchScore(log.getFaceMatchScore())
                .siteId(log.getSiteId())
                .build();
    }

    /** Maps AttendanceLog entity to AttendanceLogDto. */
    private AttendanceLogDto toLogDto(AttendanceLog log) {
        return AttendanceLogDto.builder()
                .id(log.getId())
                .employeeId(log.getEmployeeId())
                .punchType(log.getPunchType())
                .punchTime(log.getPunchTime())
                .latitude(log.getLatitude())
                .longitude(log.getLongitude())
                .accuracy(log.getAccuracy())
                .faceMatchScore(log.getFaceMatchScore())
                .insideGeofence(log.isInsideGeofence())
                .deviceInfo(log.getDeviceInfo())
                .siteId(log.getSiteId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    /**
     * Record holding paginated attendance log results.
     */
    public record PaginatedAttendanceLogs(List<AttendanceLogDto> logs,
                                           PaginationMeta meta) {
    }
}
