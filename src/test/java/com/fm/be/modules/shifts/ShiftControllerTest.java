package com.fm.be.modules.shifts;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.shifts.dto.CreateShiftDto;
import com.fm.be.modules.shifts.dto.ScheduleResponseDto;
import com.fm.be.modules.shifts.dto.ShiftAssignmentDto;
import com.fm.be.modules.shifts.dto.UpdateShiftDto;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftControllerTest {

    @Mock
    private ShiftService shiftService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private ShiftController shiftController;

    @Nested
    class CreateShift {

        @Test
        @DisplayName("delegates to shiftService.createShift and returns Shift")
        void delegatesToService() {
            CreateShiftDto dto = mock(CreateShiftDto.class);
            Shift shift = mock(Shift.class);
            when(shiftService.createShift(dto)).thenReturn(shift);

            Shift result = shiftController.createShift(dto);

            assertThat(result).isSameAs(shift);
            verify(shiftService).createShift(dto);
        }
    }

    @Nested
    class UpdateShift {

        @Test
        @DisplayName("delegates to shiftService.updateShift and returns Shift")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            UpdateShiftDto dto = mock(UpdateShiftDto.class);
            Shift shift = mock(Shift.class);
            when(shiftService.updateShift(id, dto)).thenReturn(shift);

            Shift result = shiftController.updateShift(id, dto);

            assertThat(result).isSameAs(shift);
            verify(shiftService).updateShift(id, dto);
        }
    }

    @Nested
    class ListShifts {

        @Test
        @DisplayName("calls shiftService.listShifts and returns ApiResponse.paginated")
        void returnsPaginatedResponse() {
            UUID siteId = UUID.randomUUID();
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            Shift shift = mock(Shift.class);
            List<Shift> shifts = List.of(shift);
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            when(shiftService.listShifts(siteId, pageReq))
                    .thenReturn(new ShiftService.PaginatedShifts(shifts, meta));

            ApiResponse<Object> response = shiftController.listShifts(siteId, pageReq);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(shifts);
            assertThat(response.getMeta()).isEqualTo(meta);
            verify(shiftService).listShifts(siteId, pageReq);
        }
    }

    @Nested
    class AssignEmployee {

        @Test
        @DisplayName("delegates to shiftService.assignEmployee and returns ShiftAssignment")
        void delegatesToService() {
            ShiftAssignmentDto dto = mock(ShiftAssignmentDto.class);
            ShiftAssignment assignment = mock(ShiftAssignment.class);
            when(shiftService.assignEmployee(dto)).thenReturn(assignment);

            ShiftAssignment result = shiftController.assignEmployee(dto);

            assertThat(result).isSameAs(assignment);
            verify(shiftService).assignEmployee(dto);
        }
    }

    @Nested
    class GetSchedule {

        @Test
        @DisplayName("when principal is admin and employeeId is set, uses employeeId and does not query repository")
        void adminWithEmployeeId_usesProvidedId() {
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            UUID employeeId = UUID.randomUUID();
            LocalDate startDate = LocalDate.of(2026, 4, 1);
            LocalDate endDate = LocalDate.of(2026, 4, 30);
            List<ScheduleResponseDto> schedule = List.of();
            when(shiftService.getSchedule(employeeId, startDate, endDate)).thenReturn(schedule);

            List<ScheduleResponseDto> result =
                    shiftController.getSchedule(principal, employeeId, startDate, endDate);

            assertThat(result).isSameAs(schedule);
            verify(shiftService).getSchedule(employeeId, startDate, endDate);
            verify(employeeRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("when principal is employee and employeeId is omitted, resolves id via employeeRepository")
        void employeeWithoutEmployeeId_looksUpEmployee() {
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.EMPLOYEE)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            Employee employee = Employee.builder().id(UUID.randomUUID()).build();
            LocalDate startDate = LocalDate.of(2026, 4, 1);
            LocalDate endDate = LocalDate.of(2026, 4, 30);
            List<ScheduleResponseDto> schedule = List.of();
            when(employeeRepository.findByUserId(userId)).thenReturn(Optional.of(employee));
            when(shiftService.getSchedule(employee.getId(), startDate, endDate))
                    .thenReturn(schedule);

            List<ScheduleResponseDto> result =
                    shiftController.getSchedule(principal, null, startDate, endDate);

            assertThat(result).isSameAs(schedule);
            verify(employeeRepository).findByUserId(userId);
            verify(shiftService).getSchedule(eq(employee.getId()), eq(startDate), eq(endDate));
        }
    }
}
