package com.fm.be.scheduler;

import com.fm.be.common.constants.PayrollStatus;
import com.fm.be.modules.attendance.AttendanceRepository;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.payroll.PayrollCalculationService;
import com.fm.be.modules.payroll.PayrollRunRepository;
import com.fm.be.modules.payroll.PayslipRepository;
import com.fm.be.modules.payroll.SalaryStructureRepository;
import com.fm.be.modules.payroll.entity.PayrollRun;
import com.fm.be.modules.payroll.entity.Payslip;
import com.fm.be.modules.payroll.entity.SalaryStructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Async background job for payroll computation.
 *
 * <p>Iterates all active employees in the organization, computes
 * statutory deductions (PF, ESIC, TDS, PT) per employee, generates
 * payslips, aggregates totals, and updates the run status to
 * PENDING_APPROVAL. If any individual employee computation fails,
 * the error is logged and processing continues with remaining
 * employees.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollJobScheduler {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final PayrollCalculationService calculationService;

    /**
     * Processes a payroll run asynchronously.
     *
     * <p>Flow:
     * <ol>
     *   <li>Mark run as PROCESSING</li>
     *   <li>Fetch all employees for the organization</li>
     *   <li>For each employee with a salary structure: compute attendance,
     *       calculate deductions, create payslip</li>
     *   <li>Aggregate totals on the run</li>
     *   <li>Mark run as PENDING_APPROVAL</li>
     * </ol>
     *
     * @param runId the payroll run UUID to process
     */
    @Async("payrollExecutor")
    @Transactional
    public void processPayrollRun(UUID runId) {
        log.info("Starting payroll processing: runId={}", runId);
        long startTime = System.currentTimeMillis();

        Optional<PayrollRun> optRun = payrollRunRepository.findById(runId);
        if (optRun.isEmpty()) {
            log.error("Payroll run not found: runId={}", runId);
            return;
        }

        PayrollRun run = optRun.get();
        if (run.getStatus() != PayrollStatus.DRAFT) {
            log.warn("Payroll run not in DRAFT status, skipping: runId={}, status={}",
                    runId, run.getStatus());
            return;
        }

        run.setStatus(PayrollStatus.PROCESSING);
        payrollRunRepository.save(run);

        try {
            List<Employee> employees = employeeRepository
                    .findByOrganizationId(run.getOrganizationId());

            List<Payslip> payslips = new ArrayList<>();
            int successCount = 0;
            int errorCount = 0;

            for (Employee employee : employees) {
                try {
                    Payslip payslip = processEmployee(
                            employee, run.getId(),
                            run.getMonth(), run.getYear());
                    if (payslip != null) {
                        payslips.add(payslip);
                        successCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to process payroll for employee={}: {}",
                            employee.getId(), e.getMessage(), e);
                }
            }

            payslipRepository.saveAll(payslips);
            updateRunTotals(run, payslips);

            run.setStatus(PayrollStatus.PENDING_APPROVAL);
            payrollRunRepository.save(run);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Payroll processing complete: runId={}, employees={}, "
                            + "success={}, errors={}, duration={}ms",
                    runId, employees.size(), successCount, errorCount, elapsed);

        } catch (Exception e) {
            log.error("Payroll processing failed: runId={}", runId, e);
            run.setStatus(PayrollStatus.DRAFT);
            payrollRunRepository.save(run);
        }
    }

    /**
     * Processes payroll for a single employee: computes attendance days,
     * pro-rates salary, calculates statutory deductions, and returns
     * a Payslip entity.
     *
     * @return the computed payslip, or null if the employee has no salary structure
     */
    private Payslip processEmployee(Employee employee, UUID runId,
                                     int month, int year) {
        Optional<SalaryStructure> optStructure = salaryStructureRepository
                .findByEmployeeId(employee.getId());
        if (optStructure.isEmpty()) {
            log.debug("No salary structure for employee={}, skipping",
                    employee.getId());
            return null;
        }

        SalaryStructure structure = optStructure.get();

        int totalWorkingDays = getTotalWorkingDays(month, year);
        int daysWorked = countDaysWorked(employee.getId(), month, year);
        int daysAbsent = Math.max(0, totalWorkingDays - daysWorked);

        BigDecimal ratio = totalWorkingDays > 0
                ? BigDecimal.valueOf(daysWorked)
                    .divide(BigDecimal.valueOf(totalWorkingDays), 4,
                            java.math.RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        BigDecimal grossEarnings = structure.getGrossSalary()
                .multiply(ratio)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        BigDecimal pfDeduction = calculationService
                .calculatePfEmployee(structure.getBasic().multiply(ratio)
                        .setScale(2, java.math.RoundingMode.HALF_UP));

        BigDecimal esicDeduction = calculationService
                .calculateEsicEmployee(grossEarnings);

        BigDecimal annualIncome = structure.getGrossSalary()
                .multiply(BigDecimal.valueOf(12));
        BigDecimal tdsDeduction = calculationService.calculateTds(annualIncome);

        BigDecimal ptDeduction = calculationService
                .calculatePt(grossEarnings, month);

        BigDecimal totalDeductions = pfDeduction
                .add(esicDeduction)
                .add(tdsDeduction)
                .add(ptDeduction);

        BigDecimal netPayable = grossEarnings.subtract(totalDeductions)
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return Payslip.builder()
                .payrollRunId(runId)
                .employeeId(employee.getId())
                .daysWorked(daysWorked)
                .daysAbsent(daysAbsent)
                .grossEarnings(grossEarnings)
                .pfDeduction(pfDeduction)
                .esicDeduction(esicDeduction)
                .tdsDeduction(tdsDeduction)
                .ptDeduction(ptDeduction)
                .otherDeductions(BigDecimal.ZERO)
                .netPayable(netPayable)
                .build();
    }

    /**
     * Counts the number of distinct days the employee punched IN
     * during the given month.
     */
    private int countDaysWorked(UUID employeeId, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        Instant start = firstDay.atStartOfDay(IST).toInstant();
        Instant end = lastDay.plusDays(1).atStartOfDay(IST).toInstant();

        return (int) attendanceRepository
                .findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeDesc(
                        employeeId, start, end)
                .stream()
                .filter(a -> a.getPunchType()
                        == com.fm.be.common.constants.PunchType.IN)
                .map(a -> a.getPunchTime().atZone(IST).toLocalDate())
                .distinct()
                .count();
    }

    /**
     * Returns the total number of working days in a month (weekdays only).
     */
    private int getTotalWorkingDays(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        int workingDays = 0;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            if (date.getDayOfWeek().getValue() <= 5) {
                workingDays++;
            }
        }
        return workingDays;
    }

    /**
     * Updates the payroll run with aggregated totals from all payslips.
     */
    private void updateRunTotals(PayrollRun run, List<Payslip> payslips) {
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalPf = BigDecimal.ZERO;
        BigDecimal totalEsic = BigDecimal.ZERO;
        BigDecimal totalTds = BigDecimal.ZERO;

        for (Payslip p : payslips) {
            totalGross = totalGross.add(
                    p.getGrossEarnings() != null ? p.getGrossEarnings() : BigDecimal.ZERO);
            totalNet = totalNet.add(
                    p.getNetPayable() != null ? p.getNetPayable() : BigDecimal.ZERO);
            totalPf = totalPf.add(
                    p.getPfDeduction() != null ? p.getPfDeduction() : BigDecimal.ZERO);
            totalEsic = totalEsic.add(
                    p.getEsicDeduction() != null ? p.getEsicDeduction() : BigDecimal.ZERO);
            totalTds = totalTds.add(
                    p.getTdsDeduction() != null ? p.getTdsDeduction() : BigDecimal.ZERO);
        }

        run.setTotalGross(totalGross);
        run.setTotalNet(totalNet);
        run.setTotalPf(totalPf);
        run.setTotalEsic(totalEsic);
        run.setTotalTds(totalTds);
    }
}
