package com.fm.be.scheduler;

import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.notifications.NotificationService;
import com.fm.be.modules.shifts.ShiftAssignmentRepository;
import com.fm.be.modules.shifts.ShiftRepository;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShiftReminderJob}.
 */
@ExtendWith(MockitoExtension.class)
class ShiftReminderJobTest {

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ShiftReminderJob shiftReminderJob;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID shiftId = UUID.randomUUID();

    @Test
    @DisplayName("should send reminder for shift starting within 30 minutes")
    void sendShiftReminders_upcomingShift_sendsNotification() {
        LocalTime shiftStart = LocalTime.now(ZoneId.of("Asia/Kolkata"))
                .plusMinutes(15);

        Shift shift = Shift.builder()
                .id(shiftId)
                .name("Morning Shift")
                .startTime(shiftStart)
                .isActive(true)
                .build();

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .date(LocalDate.now(ZoneId.of("Asia/Kolkata")))
                .build();

        Employee employee = Employee.builder()
                .id(employeeId)
                .userId(userId)
                .fullName("Test Employee")
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.of(shift));
        when(employeeRepository.findById(employeeId))
                .thenReturn(Optional.of(employee));

        shiftReminderJob.sendShiftReminders();

        verify(notificationService).sendToUser(
                eq(userId),
                eq("Shift Reminder"),
                anyString(),
                eq("SHIFT_REMINDER"));
    }

    @Test
    @DisplayName("should not send reminder for shift starting more than 30 minutes later")
    void sendShiftReminders_farAwayShift_doesNotSend() {
        LocalTime shiftStart = LocalTime.now(ZoneId.of("Asia/Kolkata"))
                .plusMinutes(60);

        Shift shift = Shift.builder()
                .id(shiftId)
                .name("Evening Shift")
                .startTime(shiftStart)
                .isActive(true)
                .build();

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .date(LocalDate.now(ZoneId.of("Asia/Kolkata")))
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.of(shift));

        shiftReminderJob.sendShiftReminders();

        verify(notificationService, never()).sendToUser(
                any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should skip inactive shifts")
    void sendShiftReminders_inactiveShift_skips() {
        Shift shift = Shift.builder()
                .id(shiftId)
                .name("Inactive Shift")
                .startTime(LocalTime.now(ZoneId.of("Asia/Kolkata"))
                        .plusMinutes(15))
                .isActive(false)
                .build();

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.of(shift));

        shiftReminderJob.sendShiftReminders();

        verify(notificationService, never()).sendToUser(
                any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should handle no assignments for today")
    void sendShiftReminders_noAssignments_doesNothing() {
        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        shiftReminderJob.sendShiftReminders();

        verify(notificationService, never()).sendToUser(
                any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should skip when shift not found")
    void sendShiftReminders_shiftNotFound_skips() {
        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.empty());

        shiftReminderJob.sendShiftReminders();

        verify(notificationService, never()).sendToUser(
                any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should skip when employee not found")
    void sendShiftReminders_employeeNotFound_skips() {
        LocalTime shiftStart = LocalTime.now(ZoneId.of("Asia/Kolkata"))
                .plusMinutes(15);

        Shift shift = Shift.builder()
                .id(shiftId)
                .name("Morning Shift")
                .startTime(shiftStart)
                .isActive(true)
                .build();

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.of(shift));
        when(employeeRepository.findById(employeeId))
                .thenReturn(Optional.empty());

        shiftReminderJob.sendShiftReminders();

        verify(notificationService, never()).sendToUser(
                any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should handle notification failure gracefully")
    void sendShiftReminders_notificationFails_continuesProcessing() {
        LocalTime shiftStart = LocalTime.now(ZoneId.of("Asia/Kolkata"))
                .plusMinutes(15);

        Shift shift = Shift.builder()
                .id(shiftId)
                .name("Morning Shift")
                .startTime(shiftStart)
                .isActive(true)
                .build();

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(employeeId)
                .shiftId(shiftId)
                .build();

        Employee employee = Employee.builder()
                .id(employeeId)
                .userId(userId)
                .fullName("Test Employee")
                .build();

        when(shiftAssignmentRepository.findByDate(any(LocalDate.class)))
                .thenReturn(List.of(assignment));
        when(shiftRepository.findById(shiftId))
                .thenReturn(Optional.of(shift));
        when(employeeRepository.findById(employeeId))
                .thenReturn(Optional.of(employee));
        doThrow(new RuntimeException("FCM error"))
                .when(notificationService).sendToUser(
                        any(), anyString(), anyString(), anyString());

        // Should not throw — handles error gracefully
        shiftReminderJob.sendShiftReminders();
    }
}
