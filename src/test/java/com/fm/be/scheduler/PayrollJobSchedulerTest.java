package com.fm.be.scheduler;

import com.fm.be.common.constants.PayrollStatus;
import com.fm.be.common.constants.PunchType;
import com.fm.be.modules.attendance.AttendanceRepository;
import com.fm.be.modules.attendance.entity.AttendanceLog;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.payroll.PayrollCalculationService;
import com.fm.be.modules.payroll.PayrollRunRepository;
import com.fm.be.modules.payroll.PayslipRepository;
import com.fm.be.modules.payroll.SalaryStructureRepository;
import com.fm.be.modules.payroll.entity.PayrollRun;
import com.fm.be.modules.payroll.entity.Payslip;
import com.fm.be.modules.payroll.entity.SalaryStructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PayrollJobScheduler}.
 * Covers payroll processing, error handling for individual employees,
 * and edge cases like missing salary structures.
 */
@ExtendWith(MockitoExtension.class)
class PayrollJobSchedulerTest {

    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private SalaryStructureRepository salaryStructureRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private PayrollCalculationService calculationService;

    @InjectMocks
    private PayrollJobScheduler payrollJobScheduler;

    @Captor
    private ArgumentCaptor<List<Payslip>> payslipsCaptor;

    @Captor
    private ArgumentCaptor<PayrollRun> runCaptor;

    private final UUID orgId = UUID.randomUUID();

    @Nested
    @DisplayName("processPayrollRun")
    class ProcessPayrollRunTests {

        @Test
        @DisplayName("should process all employees and generate payslips")
        void processPayrollRun_multipleEmployees_generatesPayslips() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.DRAFT, 3, 2026);

            List<Employee> employees = List.of(
                    buildEmployee(UUID.randomUUID()),
                    buildEmployee(UUID.randomUUID())
            );

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findByOrganizationId(orgId))
                    .thenReturn(employees);

            for (Employee emp : employees) {
                SalaryStructure structure = buildSalaryStructure(emp.getId());
                when(salaryStructureRepository.findByEmployeeId(emp.getId()))
                        .thenReturn(Optional.of(structure));
                when(attendanceRepository
                        .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                                eq(emp.getId()), any(Instant.class), any(Instant.class)))
                        .thenReturn(buildAttendanceLogs(20));
            }

            setupCalculationMocks();

            payrollJobScheduler.processPayrollRun(runId);

            verify(payslipRepository).saveAll(payslipsCaptor.capture());
            List<Payslip> saved = payslipsCaptor.getValue();
            assertThat(saved).hasSize(2);

            verify(payrollRunRepository, times(2)).save(runCaptor.capture());
            PayrollRun finalRun = runCaptor.getAllValues().get(1);
            assertThat(finalRun.getStatus())
                    .isEqualTo(PayrollStatus.PENDING_APPROVAL);
            assertThat(finalRun.getTotalGross()).isNotNull();
            assertThat(finalRun.getTotalGross().compareTo(BigDecimal.ZERO))
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("should skip employees without salary structure")
        void processPayrollRun_noSalaryStructure_skipsEmployee() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.DRAFT, 3, 2026);

            Employee empWithStructure = buildEmployee(UUID.randomUUID());
            Employee empWithout = buildEmployee(UUID.randomUUID());

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findByOrganizationId(orgId))
                    .thenReturn(List.of(empWithStructure, empWithout));

            when(salaryStructureRepository.findByEmployeeId(empWithStructure.getId()))
                    .thenReturn(Optional.of(
                            buildSalaryStructure(empWithStructure.getId())));
            when(salaryStructureRepository.findByEmployeeId(empWithout.getId()))
                    .thenReturn(Optional.empty());

            when(attendanceRepository
                    .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            eq(empWithStructure.getId()),
                            any(Instant.class), any(Instant.class)))
                    .thenReturn(buildAttendanceLogs(22));

            setupCalculationMocks();

            payrollJobScheduler.processPayrollRun(runId);

            verify(payslipRepository).saveAll(payslipsCaptor.capture());
            assertThat(payslipsCaptor.getValue()).hasSize(1);
        }

        @Test
        @DisplayName("should continue processing when one employee fails")
        void processPayrollRun_oneEmployeeFails_continuesWithOthers() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.DRAFT, 3, 2026);

            Employee empGood = buildEmployee(UUID.randomUUID());
            Employee empBad = buildEmployee(UUID.randomUUID());

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findByOrganizationId(orgId))
                    .thenReturn(List.of(empBad, empGood));

            when(salaryStructureRepository.findByEmployeeId(empBad.getId()))
                    .thenThrow(new RuntimeException("DB error"));
            when(salaryStructureRepository.findByEmployeeId(empGood.getId()))
                    .thenReturn(Optional.of(buildSalaryStructure(empGood.getId())));

            when(attendanceRepository
                    .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            eq(empGood.getId()),
                            any(Instant.class), any(Instant.class)))
                    .thenReturn(buildAttendanceLogs(18));

            setupCalculationMocks();

            payrollJobScheduler.processPayrollRun(runId);

            verify(payslipRepository).saveAll(payslipsCaptor.capture());
            assertThat(payslipsCaptor.getValue()).hasSize(1);

            verify(payrollRunRepository, times(2)).save(runCaptor.capture());
            PayrollRun finalRun = runCaptor.getAllValues().get(1);
            assertThat(finalRun.getStatus())
                    .isEqualTo(PayrollStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("should not process run that is not in DRAFT status")
        void processPayrollRun_notDraft_skips() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.PROCESSING, 3, 2026);

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));

            payrollJobScheduler.processPayrollRun(runId);

            verify(employeeRepository, never()).findByOrganizationId(any());
            verify(payslipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("should handle run not found gracefully")
        void processPayrollRun_runNotFound_logsError() {
            UUID runId = UUID.randomUUID();
            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.empty());

            payrollJobScheduler.processPayrollRun(runId);

            verify(employeeRepository, never()).findByOrganizationId(any());
            verify(payslipRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("should compute correct payslip deductions")
        void processPayrollRun_verifiesDeductions() {
            UUID runId = UUID.randomUUID();
            UUID empId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.DRAFT, 2, 2026);

            Employee employee = buildEmployee(empId);
            SalaryStructure structure = SalaryStructure.builder()
                    .employeeId(empId)
                    .basic(new BigDecimal("15000"))
                    .hra(new BigDecimal("6000"))
                    .grossSalary(new BigDecimal("21000"))
                    .build();

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findByOrganizationId(orgId))
                    .thenReturn(List.of(employee));
            when(salaryStructureRepository.findByEmployeeId(empId))
                    .thenReturn(Optional.of(structure));
            when(attendanceRepository
                    .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                            eq(empId), any(Instant.class), any(Instant.class)))
                    .thenReturn(buildAttendanceLogs(20));

            when(calculationService.calculatePfEmployee(any()))
                    .thenReturn(new BigDecimal("1800.00"));
            when(calculationService.calculateEsicEmployee(any()))
                    .thenReturn(new BigDecimal("157.50"));
            when(calculationService.calculateTds(any()))
                    .thenReturn(new BigDecimal("500.00"));
            when(calculationService.calculatePt(any(), eq(2)))
                    .thenReturn(new BigDecimal("300.00"));

            payrollJobScheduler.processPayrollRun(runId);

            verify(payslipRepository).saveAll(payslipsCaptor.capture());
            Payslip slip = payslipsCaptor.getValue().get(0);
            assertThat(slip.getPfDeduction())
                    .isEqualByComparingTo(new BigDecimal("1800.00"));
            assertThat(slip.getEsicDeduction())
                    .isEqualByComparingTo(new BigDecimal("157.50"));
            assertThat(slip.getTdsDeduction())
                    .isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(slip.getPtDeduction())
                    .isEqualByComparingTo(new BigDecimal("300.00"));
        }

        @Test
        @DisplayName("should revert to DRAFT on catastrophic failure")
        void processPayrollRun_catastrophicFailure_revertsStatus() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = buildRun(runId, PayrollStatus.DRAFT, 3, 2026);

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findByOrganizationId(orgId))
                    .thenThrow(new RuntimeException("DB connection lost"));

            payrollJobScheduler.processPayrollRun(runId);

            verify(payrollRunRepository, times(2)).save(runCaptor.capture());
            PayrollRun revertedRun = runCaptor.getAllValues().get(1);
            assertThat(revertedRun.getStatus()).isEqualTo(PayrollStatus.DRAFT);
        }
    }

    // ====================== Helpers ======================

    private void setupCalculationMocks() {
        when(calculationService.calculatePfEmployee(any()))
                .thenReturn(new BigDecimal("1800.00"));
        when(calculationService.calculateEsicEmployee(any()))
                .thenReturn(BigDecimal.ZERO);
        when(calculationService.calculateTds(any()))
                .thenReturn(new BigDecimal("1041.67"));
        when(calculationService.calculatePt(any(), any(int.class)))
                .thenReturn(new BigDecimal("200.00"));
    }

    private PayrollRun buildRun(UUID id, PayrollStatus status,
                                 int month, int year) {
        return PayrollRun.builder()
                .id(id)
                .month(month)
                .year(year)
                .status(status)
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Employee buildEmployee(UUID id) {
        return Employee.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .fullName("Test Employee")
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SalaryStructure buildSalaryStructure(UUID employeeId) {
        return SalaryStructure.builder()
                .id(UUID.randomUUID())
                .employeeId(employeeId)
                .basic(new BigDecimal("20000"))
                .hra(new BigDecimal("8000"))
                .grossSalary(new BigDecimal("30000"))
                .build();
    }

    private List<AttendanceLog> buildAttendanceLogs(int daysWorked) {
        List<AttendanceLog> logs = new ArrayList<>();
        ZonedDateTime baseDate = ZonedDateTime.of(
                2026, 3, 1, 9, 0, 0, 0,
                ZoneId.of("Asia/Kolkata"));

        for (int i = 0; i < daysWorked; i++) {
            ZonedDateTime day = baseDate.plusDays(i);
            if (day.getDayOfWeek().getValue() > 5) continue;

            logs.add(AttendanceLog.builder()
                    .id(UUID.randomUUID())
                    .employeeId(UUID.randomUUID())
                    .punchType(PunchType.IN)
                    .punchTime(day.toInstant())
                    .build());
        }
        return logs;
    }
}
