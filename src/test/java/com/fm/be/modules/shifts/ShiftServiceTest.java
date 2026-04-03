package com.fm.be.modules.shifts;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.shifts.dto.CreateShiftDto;
import com.fm.be.modules.shifts.dto.ScheduleResponseDto;
import com.fm.be.modules.shifts.dto.ShiftAssignmentDto;
import com.fm.be.modules.shifts.dto.UpdateShiftDto;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShiftService}.
 * Covers CRUD, assignment, schedule retrieval, and cache invalidation.
 */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ShiftAssignmentRepository assignmentRepository;
    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private ShiftService shiftService;

    private Shift testShift;
    private final UUID shiftId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testShift = Shift.builder()
                .id(shiftId)
                .siteId(siteId)
                .name("Morning Shift")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createShift")
    class CreateShiftTests {

        @Test
        @DisplayName("should create shift successfully")
        void createShift_validData_creates() {
            when(shiftRepository.save(any(Shift.class))).thenAnswer(i -> {
                Shift s = i.getArgument(0);
                s.setId(UUID.randomUUID());
                s.setCreatedAt(Instant.now());
                s.setUpdatedAt(Instant.now());
                return s;
            });

            CreateShiftDto dto = CreateShiftDto.builder()
                    .siteId(siteId)
                    .name("Night Shift")
                    .startTime(LocalTime.of(22, 0))
                    .endTime(LocalTime.of(6, 0))
                    .build();

            Shift result = shiftService.createShift(dto);

            assertThat(result.getName()).isEqualTo("Night Shift");
            assertThat(result.getSiteId()).isEqualTo(siteId);
        }
    }

    @Nested
    @DisplayName("updateShift")
    class UpdateShiftTests {

        @Test
        @DisplayName("should update shift name and times")
        void updateShift_validData_updates() {
            when(shiftRepository.findById(shiftId))
                    .thenReturn(Optional.of(testShift));
            when(shiftRepository.save(any(Shift.class)))
                    .thenAnswer(i -> i.getArgument(0));

            UpdateShiftDto dto = UpdateShiftDto.builder()
                    .name("Updated Morning")
                    .startTime(LocalTime.of(8, 0))
                    .build();

            Shift result = shiftService.updateShift(shiftId, dto);

            assertThat(result.getName()).isEqualTo("Updated Morning");
            assertThat(result.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        }

        @Test
        @DisplayName("should throw when shift not found")
        void updateShift_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(shiftRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> shiftService.updateShift(
                    randomId, new UpdateShiftDto()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("assignEmployee")
    class AssignEmployeeTests {

        @Test
        @DisplayName("should assign employee to shift on date")
        void assignEmployee_validData_assigns() {
            when(shiftRepository.existsById(shiftId)).thenReturn(true);
            when(assignmentRepository.existsByEmployeeIdAndDate(
                    employeeId, LocalDate.of(2026, 4, 5)))
                    .thenReturn(false);
            when(assignmentRepository.save(any(ShiftAssignment.class)))
                    .thenAnswer(i -> {
                        ShiftAssignment a = i.getArgument(0);
                        a.setId(UUID.randomUUID());
                        a.setCreatedAt(Instant.now());
                        return a;
                    });

            ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                    .employeeId(employeeId)
                    .shiftId(shiftId)
                    .date(LocalDate.of(2026, 4, 5))
                    .build();

            ShiftAssignment result = shiftService.assignEmployee(dto);

            assertThat(result.getEmployeeId()).isEqualTo(employeeId);
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("should throw on duplicate assignment date")
        void assignEmployee_duplicateDate_throws() {
            when(shiftRepository.existsById(shiftId)).thenReturn(true);
            when(assignmentRepository.existsByEmployeeIdAndDate(
                    employeeId, LocalDate.of(2026, 4, 5)))
                    .thenReturn(true);

            ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                    .employeeId(employeeId)
                    .shiftId(shiftId)
                    .date(LocalDate.of(2026, 4, 5))
                    .build();

            assertThatThrownBy(() -> shiftService.assignEmployee(dto))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should throw when shift not found")
        void assignEmployee_shiftNotFound_throws() {
            UUID randomShiftId = UUID.randomUUID();
            when(shiftRepository.existsById(randomShiftId)).thenReturn(false);

            ShiftAssignmentDto dto = ShiftAssignmentDto.builder()
                    .employeeId(employeeId)
                    .shiftId(randomShiftId)
                    .date(LocalDate.of(2026, 4, 5))
                    .build();

            assertThatThrownBy(() -> shiftService.assignEmployee(dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getSchedule")
    class GetScheduleTests {

        @Test
        @DisplayName("should return schedule for date range")
        void getSchedule_returnsCorrectRange() {
            ShiftAssignment assignment = ShiftAssignment.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .shiftId(shiftId)
                    .date(LocalDate.of(2026, 4, 5))
                    .createdAt(Instant.now())
                    .build();
            when(assignmentRepository.findByEmployeeIdAndDateBetween(
                    eq(employeeId), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(List.of(assignment));
            when(shiftRepository.findById(shiftId))
                    .thenReturn(Optional.of(testShift));

            List<ScheduleResponseDto> result = shiftService.getSchedule(
                    employeeId,
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 30));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getShiftName())
                    .isEqualTo("Morning Shift");
            assertThat(result.get(0).getDate())
                    .isEqualTo(LocalDate.of(2026, 4, 5));
        }
    }

    @Nested
    @DisplayName("listShifts")
    class ListShiftsTests {

        @Test
        @DisplayName("should return paginated shifts for site")
        void listShifts_returnsPaginated() {
            Page<Shift> page = new PageImpl<>(List.of(testShift));
            when(shiftRepository.findBySiteId(
                    eq(siteId), any(Pageable.class))).thenReturn(page);

            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            ShiftService.PaginatedShifts result =
                    shiftService.listShifts(siteId, pageReq);

            assertThat(result.shifts()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }
}
