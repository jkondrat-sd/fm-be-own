package com.fm.be.modules.admin;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.admin.dto.SystemConfigDto;
import com.fm.be.modules.admin.dto.SystemStatsDto;
import com.fm.be.modules.admin.entity.AuditLog;
import com.fm.be.modules.attendance.AttendanceRepository;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.payroll.PayrollRunRepository;
import com.fm.be.modules.sites.SiteRepository;
import com.fm.be.modules.sites.entity.Site;
import com.fm.be.modules.users.UserRepository;
import com.fm.be.modules.users.entity.User;
import com.fm.be.common.constants.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminService}.
 * Covers audit logs, dashboard stats, system config, and data export.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private AdminService adminService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @Nested
    @DisplayName("getAuditLogs")
    class GetAuditLogsTests {

        @Test
        @DisplayName("should return paginated audit logs without filters")
        void getAuditLogs_noFilters_returnsPaginated() {
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .action("CREATE_USER")
                    .targetEntity("User")
                    .createdAt(Instant.now())
                    .build();

            Page<AuditLog> page = new PageImpl<>(
                    List.of(log), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findAllByOrderByCreatedAtDesc(
                    any(PageRequest.class))).thenReturn(page);

            AdminService.PaginatedAuditLogs result =
                    adminService.getAuditLogs(null, null, pageReq);

            assertThat(result.logs()).hasSize(1);
            assertThat(result.logs().get(0).getAction()).isEqualTo("CREATE_USER");
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("should filter audit logs by userId")
        void getAuditLogs_filterByUserId_returnsFiltered() {
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            AuditLog log = AuditLog.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .action("UPDATE_USER")
                    .targetEntity("User")
                    .createdAt(Instant.now())
                    .build();

            Page<AuditLog> page = new PageImpl<>(
                    List.of(log), PageRequest.of(0, 10), 1);
            when(auditLogRepository.findByUserIdOrderByCreatedAtDesc(
                    eq(userId), any(PageRequest.class))).thenReturn(page);

            AdminService.PaginatedAuditLogs result =
                    adminService.getAuditLogs(userId, null, pageReq);

            assertThat(result.logs()).hasSize(1);
            verify(auditLogRepository).findByUserIdOrderByCreatedAtDesc(
                    eq(userId), any(PageRequest.class));
        }

        @Test
        @DisplayName("should filter audit logs by action")
        void getAuditLogs_filterByAction_returnsFiltered() {
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            Page<AuditLog> page = new PageImpl<>(
                    List.of(), PageRequest.of(0, 10), 0);
            when(auditLogRepository.findByActionOrderByCreatedAtDesc(
                    eq("EXPORT_DATA"), any(PageRequest.class)))
                    .thenReturn(page);

            AdminService.PaginatedAuditLogs result =
                    adminService.getAuditLogs(null, "EXPORT_DATA", pageReq);

            assertThat(result.logs()).isEmpty();
            verify(auditLogRepository).findByActionOrderByCreatedAtDesc(
                    eq("EXPORT_DATA"), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("getDashboardStats")
    class GetDashboardStatsTests {

        @Test
        @DisplayName("should return aggregated system statistics")
        void getDashboardStats_returnsStats() {
            when(userRepository.count()).thenReturn(50L);
            when(employeeRepository.count()).thenReturn(30L);
            when(siteRepository.count()).thenReturn(10L);
            when(payrollRunRepository.count()).thenReturn(5L);
            when(attendanceRepository.countByPunchTimeBetween(
                    any(Instant.class), any(Instant.class))).thenReturn(120L);

            SystemStatsDto stats = adminService.getDashboardStats();

            assertThat(stats.getTotalUsers()).isEqualTo(50);
            assertThat(stats.getTotalEmployees()).isEqualTo(30);
            assertThat(stats.getTotalSites()).isEqualTo(10);
            assertThat(stats.getTotalPayrollRuns()).isEqualTo(5);
            assertThat(stats.getTodayAttendanceCount()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("systemConfig")
    class SystemConfigTests {

        @Test
        @DisplayName("should retrieve system config from Redis")
        void getSystemConfig_returnsConfig() {
            Map<Object, Object> rawConfig = new HashMap<>();
            rawConfig.put("maxUploadSize", "10MB");
            rawConfig.put("featureFlag.newUI", "true");

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(anyString())).thenReturn(rawConfig);

            SystemConfigDto config = adminService.getSystemConfig();

            assertThat(config.getConfig()).containsKey("maxUploadSize");
            assertThat(config.getConfig().get("maxUploadSize"))
                    .isEqualTo("10MB");
        }

        @Test
        @DisplayName("should update system config in Redis and log audit")
        void updateSystemConfig_updatesAndLogsAudit() {
            Map<String, Object> newConfig = Map.of("maxUploadSize", "20MB");
            SystemConfigDto dto = SystemConfigDto.builder()
                    .config(newConfig).build();

            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries(anyString()))
                    .thenReturn(Map.of("maxUploadSize", "20MB"));

            SystemConfigDto result = adminService.updateSystemConfig(
                    dto, userId, "192.168.1.1");

            assertThat(result.getConfig()).containsKey("maxUploadSize");
            verify(hashOperations).putAll(anyString(), any(Map.class));
            verify(auditLogRepository).save(any(AuditLog.class));
        }
    }

    @Nested
    @DisplayName("exportData")
    class ExportDataTests {

        @Test
        @DisplayName("should export users as CSV")
        void exportData_users_returnsCsv() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email("test@example.com")
                    .phone("9876543210")
                    .role(Role.ADMIN)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .build();
            when(userRepository.findAll()).thenReturn(List.of(user));

            String csv = adminService.exportData("users", userId, "127.0.0.1");

            assertThat(csv).contains("id,email,phone,role,isActive,createdAt");
            assertThat(csv).contains("test@example.com");
            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("should export employees as CSV")
        void exportData_employees_returnsCsv() {
            Employee emp = Employee.builder()
                    .id(UUID.randomUUID())
                    .fullName("John Doe")
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .build();
            when(employeeRepository.findAll()).thenReturn(List.of(emp));

            String csv = adminService.exportData(
                    "employees", userId, "127.0.0.1");

            assertThat(csv).contains("id,fullName,siteId");
            assertThat(csv).contains("John Doe");
        }

        @Test
        @DisplayName("should export sites as CSV")
        void exportData_sites_returnsCsv() {
            Site site = Site.builder()
                    .id(UUID.randomUUID())
                    .name("Main Office")
                    .isActive(true)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .build();
            when(siteRepository.findAll()).thenReturn(List.of(site));

            String csv = adminService.exportData("sites", userId, "127.0.0.1");

            assertThat(csv).contains("id,name,address");
            assertThat(csv).contains("Main Office");
        }

        @Test
        @DisplayName("should throw for unsupported entity type")
        void exportData_unknownEntity_throws() {
            assertThatThrownBy(() ->
                    adminService.exportData("invoices", userId, "127.0.0.1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("logAuditEvent")
    class LogAuditEventTests {

        @Test
        @DisplayName("should persist audit log entry")
        void logAuditEvent_persistsEntry() {
            adminService.logAuditEvent(
                    userId, "DELETE_USER", "User",
                    UUID.randomUUID(),
                    Map.of("reason", "test"),
                    "10.0.0.1");

            verify(auditLogRepository).save(any(AuditLog.class));
        }
    }
}
