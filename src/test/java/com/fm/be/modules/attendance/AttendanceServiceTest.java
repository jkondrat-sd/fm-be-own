package com.fm.be.modules.attendance;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.constants.PunchType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AttendanceService}.
 * Covers punch (geofence, face match, dedup), reports, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private Employee testEmployee;
    private Site testSite;
    private final UUID employeeId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testEmployee = Employee.builder()
                .id(employeeId)
                .userId(UUID.randomUUID())
                .fullName("John Doe")
                .siteId(siteId)
                .organizationId(orgId)
                .build();

        testSite = Site.builder()
                .id(siteId)
                .name("Test Site")
                .latitude(19.0760)
                .longitude(72.8777)
                .geofenceRadius(500.0)
                .isActive(true)
                .organizationId(orgId)
                .build();
    }

    @Nested
    @DisplayName("punch")
    class PunchTests {

        @Test
        @DisplayName("should record punch when inside geofence")
        void punch_insideGeofence_recordsPunch() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            // Simulate PostGIS geofence check passing
            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), any(Double.class), any(Double.class)))
                    .thenReturn(true);
            when(attendanceRepository.findRecentDuplicate(
                    any(), any(), any())).thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceLog.class)))
                    .thenAnswer(i -> {
                        AttendanceLog log = i.getArgument(0);
                        log.setId(UUID.randomUUID());
                        log.setCreatedAt(Instant.now());
                        return log;
                    });

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .faceMatchScore(0.92)
                    .build();

            PunchResponseDto result = attendanceService.punch(
                    employeeId, dto);

            assertThat(result).isNotNull();
            assertThat(result.getPunchType()).isEqualTo(PunchType.IN);
            assertThat(result.isInsideGeofence()).isTrue();
        }

        @Test
        @DisplayName("should throw GEOFENCE_VIOLATION when outside")
        void punch_outsideGeofence_throwsViolation() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), any(Double.class), any(Double.class)))
                    .thenReturn(false);

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(20.0)
                    .longitude(73.0)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ErrorCode.GEOFENCE_VIOLATION);
        }

        @Test
        @DisplayName("should throw FACE_MISMATCH when score below threshold")
        void punch_faceMismatch_throwsMismatch() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), any(Double.class), any(Double.class)))
                    .thenReturn(true);

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .faceMatchScore(0.60)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ErrorCode.FACE_MISMATCH);
        }

        @Test
        @DisplayName("should throw DUPLICATE_PUNCH within 5-min window")
        void punch_duplicateWithinWindow_throwsDuplicate() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), any(Double.class), any(Double.class)))
                    .thenReturn(true);
            when(attendanceRepository.findRecentDuplicate(
                    any(), any(), any()))
                    .thenReturn(Optional.of(new AttendanceLog()));

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .faceMatchScore(0.92)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ErrorCode.DUPLICATE_PUNCH);
        }

        @Test
        @DisplayName("should throw when employee not found")
        void punch_employeeNotFound_throws() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.empty());

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw SITE_INACTIVE when site deactivated")
        void punch_siteInactive_throws() {
            testSite.setActive(false);
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ErrorCode.SITE_INACTIVE);
        }

        @Test
        @DisplayName("should throw when employee has no site assigned")
        void punch_noSiteAssigned_throws() {
            testEmployee.setSiteId(null);
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.IN)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .build();

            assertThatThrownBy(() -> attendanceService.punch(employeeId, dto))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("should allow punch without face score (optional)")
        void punch_noFaceScore_allowsPunch() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.isPointInsideGeofence(
                    eq(siteId), any(Double.class), any(Double.class)))
                    .thenReturn(true);
            when(attendanceRepository.findRecentDuplicate(
                    any(), any(), any())).thenReturn(Optional.empty());
            when(attendanceRepository.save(any(AttendanceLog.class)))
                    .thenAnswer(i -> {
                        AttendanceLog log = i.getArgument(0);
                        log.setId(UUID.randomUUID());
                        log.setCreatedAt(Instant.now());
                        return log;
                    });

            PunchRequestDto dto = PunchRequestDto.builder()
                    .punchType(PunchType.OUT)
                    .latitude(19.0761)
                    .longitude(72.8778)
                    .build();

            PunchResponseDto result = attendanceService.punch(
                    employeeId, dto);
            assertThat(result.getPunchType()).isEqualTo(PunchType.OUT);
        }
    }

    @Nested
    @DisplayName("getTodayAttendance")
    class TodayAttendanceTests {

        @Test
        @DisplayName("should return today's punches for employee")
        void getTodayAttendance_returnsTodayPunches() {
            when(employeeRepository.existsById(employeeId)).thenReturn(true);

            AttendanceLog log = AttendanceLog.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .punchType(PunchType.IN)
                    .punchTime(Instant.now())
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .isInsideGeofence(true)
                    .siteId(siteId)
                    .createdAt(Instant.now())
                    .build();

            when(attendanceRepository
                    .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            eq(employeeId), any(Instant.class),
                            any(Instant.class)))
                    .thenReturn(List.of(log));

            List<AttendanceLogDto> result =
                    attendanceService.getTodayAttendance(employeeId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPunchType()).isEqualTo(PunchType.IN);
        }

        @Test
        @DisplayName("should throw when employee not found")
        void getTodayAttendance_notFound_throws() {
            when(employeeRepository.existsById(employeeId)).thenReturn(false);

            assertThatThrownBy(() ->
                    attendanceService.getTodayAttendance(employeeId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getAttendanceReport")
    class AttendanceReportTests {

        @Test
        @DisplayName("should return aggregated report for a site")
        void getAttendanceReport_returnsCorrectAggregates() {
            when(siteRepository.findById(siteId))
                    .thenReturn(Optional.of(testSite));
            when(attendanceRepository
                    .countBySiteIdAndPunchTypeAndPunchTimeBetween(
                            eq(siteId), eq(PunchType.IN),
                            any(Instant.class), any(Instant.class)))
                    .thenReturn(50L);
            when(attendanceRepository
                    .countBySiteIdAndPunchTypeAndPunchTimeBetween(
                            eq(siteId), eq(PunchType.OUT),
                            any(Instant.class), any(Instant.class)))
                    .thenReturn(48L);
            when(employeeRepository.countBySiteId(siteId)).thenReturn(10L);

            Instant start = Instant.now().minusSeconds(86400 * 7);
            Instant end = Instant.now();

            AttendanceReportDto report =
                    attendanceService.getAttendanceReport(siteId, start, end);

            assertThat(report.getSiteId()).isEqualTo(siteId);
            assertThat(report.getTotalPunchIns()).isEqualTo(50);
            assertThat(report.getTotalPunchOuts()).isEqualTo(48);
            assertThat(report.getTotalEmployees()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("getSiteAttendance")
    class SiteAttendanceTests {

        @Test
        @DisplayName("should throw when site not found")
        void getSiteAttendance_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.existsById(randomId)).thenReturn(false);

            assertThatThrownBy(() ->
                    attendanceService.getSiteAttendance(randomId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should return today's logs for an existing site")
        void getSiteAttendance_found_returnsTodayLogs() {
            when(siteRepository.existsById(siteId)).thenReturn(true);

            AttendanceLog log = AttendanceLog.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .punchType(PunchType.IN)
                    .punchTime(Instant.now())
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .isInsideGeofence(true)
                    .siteId(siteId)
                    .createdAt(Instant.now())
                    .build();

            when(attendanceRepository
                    .findBySiteIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            eq(siteId), any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(log));

            List<AttendanceLogDto> result =
                    attendanceService.getSiteAttendance(siteId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmployeeId()).isEqualTo(employeeId);
        }
    }

    @Nested
    @DisplayName("getAttendanceLogs")
    class AttendanceLogsTests {

        @Test
        @DisplayName("should return paginated logs filtered by employee")
        void getAttendanceLogs_byEmployee_returnsPaginated() {
            Instant start = Instant.now().minusSeconds(86400);
            Instant end = Instant.now();
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            AttendanceLog log = AttendanceLog.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .punchType(PunchType.IN)
                    .punchTime(Instant.now())
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .isInsideGeofence(true)
                    .siteId(siteId)
                    .createdAt(Instant.now())
                    .build();

            Page<AttendanceLog> page = new PageImpl<>(
                    List.of(log), PageRequest.of(0, 10), 1);
            when(attendanceRepository.findByEmployeeIdAndPunchTimeBetween(
                    eq(employeeId), any(Instant.class), any(Instant.class),
                    any(PageRequest.class)))
                    .thenReturn(page);

            AttendanceService.PaginatedAttendanceLogs result =
                    attendanceService.getAttendanceLogs(
                            null, employeeId, start, end, pageReq);

            assertThat(result.logs()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return paginated logs filtered by site")
        void getAttendanceLogs_bySite_returnsPaginated() {
            Instant start = Instant.now().minusSeconds(86400);
            Instant end = Instant.now();
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).sortBy("punchTime")
                    .sortDirection("ASC").build();

            Page<AttendanceLog> page = new PageImpl<>(
                    List.of(), PageRequest.of(0, 20), 0);
            when(attendanceRepository.findBySiteIdAndPunchTimeBetween(
                    eq(siteId), any(Instant.class), any(Instant.class),
                    any(PageRequest.class)))
                    .thenReturn(page);

            AttendanceService.PaginatedAttendanceLogs result =
                    attendanceService.getAttendanceLogs(
                            siteId, null, start, end, pageReq);

            assertThat(result.logs()).isEmpty();
            assertThat(result.meta().getTotal()).isZero();
        }

        @Test
        @DisplayName("should return all logs when no filter provided")
        void getAttendanceLogs_noFilter_returnsAll() {
            Instant start = Instant.now().minusSeconds(86400);
            Instant end = Instant.now();
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            Page<AttendanceLog> page = new PageImpl<>(
                    List.of(), PageRequest.of(0, 20), 0);
            when(attendanceRepository.findAll(any(PageRequest.class)))
                    .thenReturn(page);

            AttendanceService.PaginatedAttendanceLogs result =
                    attendanceService.getAttendanceLogs(
                            null, null, start, end, pageReq);

            assertThat(result.logs()).isEmpty();
        }
    }
}
