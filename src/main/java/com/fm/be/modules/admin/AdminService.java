package com.fm.be.modules.admin;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.admin.dto.AuditLogResponseDto;
import com.fm.be.modules.admin.dto.SystemConfigDto;
import com.fm.be.modules.admin.dto.SystemStatsDto;
import com.fm.be.modules.admin.entity.AuditLog;
import com.fm.be.modules.attendance.AttendanceRepository;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.payroll.PayrollRunRepository;
import com.fm.be.modules.sites.SiteRepository;
import com.fm.be.modules.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for admin operations: audit log queries, system stats,
 * system config management, and data export.
 *
 * <p>User CRUD is delegated to {@link com.fm.be.modules.users.UserService}.
 * This service focuses on admin-only capabilities like audit trails,
 * dashboard statistics, and runtime configuration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String CONFIG_KEY_PREFIX = "sys:config:";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final SiteRepository siteRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Retrieves paginated audit logs, optionally filtered by user or action.
     *
     * @param userId optional user ID filter
     * @param action optional action filter
     * @param pageRequest pagination parameters
     * @return paginated audit log results
     */
    @Transactional(readOnly = true)
    public PaginatedAuditLogs getAuditLogs(UUID userId, String action,
                                            PageRequestDto pageRequest) {
        PageRequest pageable = PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit());

        Page<AuditLog> page;
        if (userId != null) {
            page = auditLogRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, pageable);
        } else if (action != null && !action.isBlank()) {
            page = auditLogRepository.findByActionOrderByCreatedAtDesc(
                    action, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<AuditLogResponseDto> logs = page.getContent().stream()
                .map(this::toAuditLogDto)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedAuditLogs(logs, meta);
    }

    /**
     * Computes system-wide dashboard statistics.
     *
     * @return aggregated stats DTO
     */
    @Transactional(readOnly = true)
    public SystemStatsDto getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalEmployees = employeeRepository.count();
        long totalSites = siteRepository.count();
        long totalPayrollRuns = payrollRunRepository.count();

        LocalDate today = LocalDate.now(IST);
        Instant dayStart = today.atStartOfDay(IST).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(IST).toInstant();

        long todayAttendance = attendanceRepository
                .countByPunchTimeBetween(dayStart, dayEnd);

        return SystemStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(totalUsers)
                .totalEmployees(totalEmployees)
                .totalSites(totalSites)
                .activeSites(totalSites)
                .totalClients(0)
                .todayAttendanceCount(todayAttendance)
                .totalPayrollRuns(totalPayrollRuns)
                .build();
    }

    /**
     * Retrieves the current system configuration from Redis.
     *
     * @return the config map
     */
    public SystemConfigDto getSystemConfig() {
        Map<Object, Object> rawConfig = redisTemplate.opsForHash()
                .entries(CONFIG_KEY_PREFIX + "global");

        Map<String, Object> config = new HashMap<>();
        rawConfig.forEach((k, v) -> config.put(String.valueOf(k), v));

        return SystemConfigDto.builder().config(config).build();
    }

    /**
     * Updates system configuration entries in Redis.
     * Merges provided entries with existing config.
     *
     * @param dto the config entries to update
     * @param actingUserId the user performing the update (for audit)
     * @param ipAddress the request IP
     * @return the updated config
     */
    public SystemConfigDto updateSystemConfig(SystemConfigDto dto,
                                               UUID actingUserId,
                                               String ipAddress) {
        String key = CONFIG_KEY_PREFIX + "global";

        Map<String, String> stringEntries = new HashMap<>();
        dto.getConfig().forEach((k, v) ->
                stringEntries.put(k, String.valueOf(v)));

        redisTemplate.opsForHash().putAll(key, stringEntries);

        logAuditEvent(actingUserId, "UPDATE_CONFIG", "SystemConfig",
                null, dto.getConfig(), ipAddress);

        log.info("System config updated by userId={}, keys={}",
                actingUserId, dto.getConfig().keySet());

        return getSystemConfig();
    }

    /**
     * Generates a CSV export for the requested entity type.
     * Returns the CSV content as a string.
     *
     * @param entity the entity type to export ("users", "employees", "sites", "attendance")
     * @param actingUserId the user performing the export (for audit)
     * @param ipAddress the request IP
     * @return CSV content string
     */
    @Transactional(readOnly = true)
    public String exportData(String entity, UUID actingUserId,
                             String ipAddress) {
        String csv;

        switch (entity.toLowerCase()) {
            case "users" -> csv = exportUsers();
            case "employees" -> csv = exportEmployees();
            case "sites" -> csv = exportSites();
            default -> throw new ResourceNotFoundException(
                    "Export entity", "type", entity);
        }

        logAuditEvent(actingUserId, "EXPORT_DATA", entity,
                null, Map.of("entity", entity), ipAddress);

        log.info("Data exported: entity={}, userId={}", entity, actingUserId);
        return csv;
    }

    /**
     * Logs an admin write operation to the audit_logs table.
     *
     * @param userId the acting user
     * @param action the action performed
     * @param targetEntity the entity type affected
     * @param targetId the specific entity ID (nullable)
     * @param metadata additional context
     * @param ipAddress the request IP
     */
    @Transactional
    public void logAuditEvent(UUID userId, String action,
                               String targetEntity, UUID targetId,
                               Map<String, Object> metadata,
                               String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .metadata(metadata)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);

        log.debug("Audit logged: action={}, entity={}, targetId={}",
                action, targetEntity, targetId);
    }

    private String exportUsers() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,email,phone,role,isActive,createdAt\n");
        userRepository.findAll().forEach(u ->
                csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                        u.getId(),
                        u.getEmail() != null ? u.getEmail() : "",
                        u.getPhone() != null ? u.getPhone() : "",
                        u.getRole(),
                        u.isActive(),
                        u.getCreatedAt())));
        return csv.toString();
    }

    private String exportEmployees() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,fullName,siteId,organizationId,createdAt\n");
        employeeRepository.findAll().forEach(e ->
                csv.append(String.format("%s,%s,%s,%s,%s\n",
                        e.getId(),
                        e.getFullName(),
                        e.getSiteId() != null ? e.getSiteId() : "",
                        e.getOrganizationId(),
                        e.getCreatedAt())));
        return csv.toString();
    }

    private String exportSites() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,name,address,isActive,organizationId,createdAt\n");
        siteRepository.findAll().forEach(s ->
                csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                        s.getId(),
                        s.getName(),
                        s.getAddress() != null ? s.getAddress() : "",
                        s.isActive(),
                        s.getOrganizationId(),
                        s.getCreatedAt())));
        return csv.toString();
    }

    private AuditLogResponseDto toAuditLogDto(AuditLog log) {
        return AuditLogResponseDto.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .action(log.getAction())
                .targetEntity(log.getTargetEntity())
                .targetId(log.getTargetId())
                .metadata(log.getMetadata())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }

    /**
     * Record holding paginated audit log results.
     */
    public record PaginatedAuditLogs(List<AuditLogResponseDto> logs,
                                      PaginationMeta meta) {
    }
}
