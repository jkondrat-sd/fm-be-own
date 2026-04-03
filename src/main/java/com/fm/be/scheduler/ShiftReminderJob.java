package com.fm.be.scheduler;

import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.notifications.NotificationService;
import com.fm.be.modules.shifts.ShiftAssignmentRepository;
import com.fm.be.modules.shifts.ShiftRepository;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job for sending shift reminder notifications.
 *
 * <p>Runs every minute. Checks all shift assignments for today,
 * and sends a push notification to employees whose shifts start
 * within the next 30 minutes.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftReminderJob {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int REMINDER_WINDOW_MINUTES = 30;

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    /**
     * Checks for upcoming shifts and sends FCM reminder notifications.
     * Fires every 60 seconds.
     */
    @Scheduled(fixedRate = 60_000)
    public void sendShiftReminders() {
        LocalDate today = LocalDate.now(IST);
        LocalTime now = LocalTime.now(IST);
        LocalTime windowEnd = now.plusMinutes(REMINDER_WINDOW_MINUTES);

        List<ShiftAssignment> todayAssignments =
                shiftAssignmentRepository.findByDate(today);

        int remindersSent = 0;

        for (ShiftAssignment assignment : todayAssignments) {
            Optional<Shift> optShift = shiftRepository
                    .findById(assignment.getShiftId());
            if (optShift.isEmpty() || !optShift.get().isActive()) {
                continue;
            }

            Shift shift = optShift.get();
            LocalTime shiftStart = shift.getStartTime();

            // Send reminder if shift starts within the 30-min window
            // and hasn't started yet
            if (shiftStart.isAfter(now) && !shiftStart.isAfter(windowEnd)) {
                Optional<Employee> optEmployee = employeeRepository
                        .findById(assignment.getEmployeeId());
                if (optEmployee.isEmpty()) {
                    continue;
                }

                Employee employee = optEmployee.get();
                try {
                    notificationService.sendToUser(
                            employee.getUserId(),
                            "Shift Reminder",
                            String.format("Your shift '%s' starts at %s",
                                    shift.getName(), shiftStart),
                            "SHIFT_REMINDER");
                    remindersSent++;
                } catch (Exception e) {
                    log.warn("Failed to send shift reminder: "
                                    + "employeeId={}, shiftId={}",
                            employee.getId(), shift.getId(), e);
                }
            }
        }

        if (remindersSent > 0) {
            log.info("Shift reminders sent: count={}", remindersSent);
        }
    }
}
