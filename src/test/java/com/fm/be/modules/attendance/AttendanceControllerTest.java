package com.fm.be.modules.attendance;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.attendance.dto.AttendanceLogDto;
import com.fm.be.modules.attendance.dto.AttendanceReportDto;
import com.fm.be.modules.attendance.dto.PunchRequestDto;
import com.fm.be.modules.attendance.dto.PunchResponseDto;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AttendanceController attendanceController;

    @Nested
    @DisplayName("punch")
    class PunchTests {

        @Test
        @DisplayName("resolves employee by user id and delegates punch to AttendanceService")
        void delegatesToService() {
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.EMPLOYEE)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            Employee employee = Employee.builder().id(UUID.randomUUID()).build();
            PunchRequestDto dto = mock(PunchRequestDto.class);
            PunchResponseDto expected = mock(PunchResponseDto.class);

            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(attendanceService.punch(employee.getId(), dto)).thenReturn(expected);

            PunchResponseDto result = attendanceController.punch(principal, dto);

            verify(employeeRepository).findByUserId(userId);
            verify(attendanceService).punch(employee.getId(), dto);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getTodayAttendance")
    class GetTodayAttendanceTests {

        @Test
        @DisplayName("resolves employee by user id and delegates to AttendanceService.getTodayAttendance")
        void delegatesToService() {
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.EMPLOYEE)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            Employee employee = Employee.builder().id(UUID.randomUUID()).build();
            List<AttendanceLogDto> expected = List.of(mock(AttendanceLogDto.class));

            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(attendanceService.getTodayAttendance(employee.getId())).thenReturn(expected);

            List<AttendanceLogDto> result = attendanceController.getTodayAttendance(principal);

            verify(employeeRepository).findByUserId(userId);
            verify(attendanceService).getTodayAttendance(employee.getId());
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getAttendanceLogs")
    class GetAttendanceLogsTests {

        @Test
        @DisplayName("delegates to AttendanceService and returns paginated ApiResponse with logs and meta")
        void delegatesToService() {
            UUID siteId = UUID.randomUUID();
            UUID employeeId = UUID.randomUUID();
            Instant start = Instant.parse("2026-04-01T00:00:00Z");
            Instant end = Instant.parse("2026-04-02T00:00:00Z");
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            List<AttendanceLogDto> logs = List.of();
            AttendanceService.PaginatedAttendanceLogs paginated =
                    new AttendanceService.PaginatedAttendanceLogs(logs, meta);

            when(attendanceService.getAttendanceLogs(siteId, employeeId, start, end, pageReq))
                    .thenReturn(paginated);

            ApiResponse<Object> response =
                    attendanceController.getAttendanceLogs(siteId, employeeId, start, end, pageReq);

            verify(attendanceService).getAttendanceLogs(siteId, employeeId, start, end, pageReq);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(logs);
            assertThat(response.getMeta()).isEqualTo(meta);
        }
    }

    @Nested
    @DisplayName("getAttendanceReport")
    class GetAttendanceReportTests {

        @Test
        @DisplayName("delegates to AttendanceService.getAttendanceReport")
        void delegatesToService() {
            UUID siteId = UUID.randomUUID();
            Instant start = Instant.parse("2026-04-01T00:00:00Z");
            Instant end = Instant.parse("2026-04-02T00:00:00Z");
            AttendanceReportDto expected = mock(AttendanceReportDto.class);
            when(attendanceService.getAttendanceReport(siteId, start, end)).thenReturn(expected);

            AttendanceReportDto result =
                    attendanceController.getAttendanceReport(siteId, start, end);

            verify(attendanceService).getAttendanceReport(siteId, start, end);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getSiteAttendance")
    class GetSiteAttendanceTests {

        @Test
        @DisplayName("delegates to AttendanceService.getSiteAttendance")
        void delegatesToService() {
            UUID siteId = UUID.randomUUID();
            List<AttendanceLogDto> expected = List.of(mock(AttendanceLogDto.class));
            when(attendanceService.getSiteAttendance(siteId)).thenReturn(expected);

            List<AttendanceLogDto> result = attendanceController.getSiteAttendance(siteId);

            verify(attendanceService).getSiteAttendance(siteId);
            assertThat(result).isSameAs(expected);
        }
    }
}
