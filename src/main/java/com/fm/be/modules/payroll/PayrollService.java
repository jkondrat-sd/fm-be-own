package com.fm.be.modules.payroll;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.constants.PayrollStatus;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.payroll.dto.CreateSalaryStructureDto;
import com.fm.be.modules.payroll.dto.PayrollRunResponseDto;
import com.fm.be.modules.payroll.dto.PayslipResponseDto;
import com.fm.be.modules.payroll.dto.SalaryStructureResponseDto;
import com.fm.be.modules.payroll.entity.PayrollRun;
import com.fm.be.modules.payroll.entity.Payslip;
import com.fm.be.modules.payroll.entity.SalaryStructure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for payroll management — salary structures, payroll runs,
 * approvals, and payslip queries.
 *
 * <p>Payroll runs are initiated via API, processed asynchronously
 * by the {@code PayrollJobScheduler}, and then approved/rejected
 * by admins.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final SalaryStructureRepository salaryStructureRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollCalculationService calculationService;
    private final com.fm.be.scheduler.PayrollJobScheduler payrollJobScheduler;

    // ====================== Salary Structures ======================

    /**
     * Creates a salary structure for an employee.
     * Automatically computes PF, ESIC, and PT deductions.
     *
     * @param dto the salary data
     * @return the created salary structure
     */
    @Transactional
    public SalaryStructureResponseDto createSalaryStructure(
            CreateSalaryStructureDto dto) {
        if (salaryStructureRepository.existsByEmployeeId(
                dto.getEmployeeId())) {
            throw new DuplicateResourceException(
                    "SalaryStructure", "employeeId", dto.getEmployeeId());
        }

        BigDecimal gross = computeGross(dto);
        BigDecimal pfEmp = calculationService.calculatePfEmployee(dto.getBasic());
        BigDecimal pfEr = calculationService.calculatePfEmployer(dto.getBasic());
        BigDecimal esicEmp = calculationService.calculateEsicEmployee(gross);
        BigDecimal esicEr = calculationService.calculateEsicEmployer(gross);
        // Default PT for non-February month
        BigDecimal pt = calculationService.calculatePt(gross, 1);

        SalaryStructure structure = SalaryStructure.builder()
                .employeeId(dto.getEmployeeId())
                .basic(dto.getBasic())
                .hra(dto.getHra())
                .conveyance(dto.getConveyance())
                .special(dto.getSpecial())
                .grossSalary(gross)
                .pfEmployee(pfEmp)
                .pfEmployer(pfEr)
                .esicEmployee(esicEmp)
                .esicEmployer(esicEr)
                .ptAmount(pt)
                .build();
        structure = salaryStructureRepository.save(structure);

        log.info("Salary structure created: employee={}, gross={}",
                dto.getEmployeeId(), gross);
        return toSalaryResponse(structure);
    }

    /**
     * Gets the salary structure for an employee.
     */
    @Transactional(readOnly = true)
    public SalaryStructureResponseDto getSalaryStructure(UUID employeeId) {
        SalaryStructure structure = salaryStructureRepository
                .findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SalaryStructure", "employeeId", employeeId));
        return toSalaryResponse(structure);
    }

    /**
     * Updates a salary structure (recalculates deductions).
     */
    @Transactional
    public SalaryStructureResponseDto updateSalaryStructure(
            UUID employeeId, CreateSalaryStructureDto dto) {
        SalaryStructure structure = salaryStructureRepository
                .findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SalaryStructure", "employeeId", employeeId));

        BigDecimal gross = computeGross(dto);
        structure.setBasic(dto.getBasic());
        structure.setHra(dto.getHra());
        structure.setConveyance(dto.getConveyance());
        structure.setSpecial(dto.getSpecial());
        structure.setGrossSalary(gross);
        structure.setPfEmployee(
                calculationService.calculatePfEmployee(dto.getBasic()));
        structure.setPfEmployer(
                calculationService.calculatePfEmployer(dto.getBasic()));
        structure.setEsicEmployee(
                calculationService.calculateEsicEmployee(gross));
        structure.setEsicEmployer(
                calculationService.calculateEsicEmployer(gross));
        structure.setPtAmount(calculationService.calculatePt(gross, 1));

        structure = salaryStructureRepository.save(structure);
        log.info("Salary structure updated: employee={}", employeeId);
        return toSalaryResponse(structure);
    }

    // ====================== Payroll Runs ======================

    /**
     * Initiates a payroll run. Returns immediately with run ID;
     * processing happens asynchronously.
     *
     * @param month payroll month (1-12)
     * @param year payroll year
     * @param organizationId the organization UUID
     * @param processedBy the admin user UUID who initiated the run
     * @return the created payroll run
     */
    @Transactional
    public PayrollRunResponseDto initiatePayrollRun(int month, int year,
                                                     UUID organizationId,
                                                     UUID processedBy) {
        if (payrollRunRepository.existsByMonthAndYearAndOrganizationId(
                month, year, organizationId)) {
            throw new DuplicateResourceException(
                    "PayrollRun", "month+year",
                    month + "/" + year);
        }

        PayrollRun run = PayrollRun.builder()
                .month(month)
                .year(year)
                .organizationId(organizationId)
                .processedBy(processedBy)
                .status(PayrollStatus.DRAFT)
                .build();
        run = payrollRunRepository.save(run);

        log.info("Payroll run initiated: id={}, period={}/{}",
                run.getId(), month, year);

        payrollJobScheduler.processPayrollRun(run.getId());

        return toRunResponse(run);
    }

    /**
     * Gets the status of a payroll run (for polling).
     */
    @Transactional(readOnly = true)
    public PayrollRunResponseDto getPayrollRunStatus(UUID runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollRun", "id", runId));
        return toRunResponse(run);
    }

    /**
     * Approves a payroll run (only if PENDING_APPROVAL).
     */
    @Transactional
    public PayrollRunResponseDto approvePayrollRun(UUID runId,
                                                    UUID approvedBy) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollRun", "id", runId));

        if (run.getStatus() != PayrollStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(ErrorCode.PAYROLL_LOCKED,
                    "Payroll run is not in PENDING_APPROVAL status: "
                            + run.getStatus());
        }

        run.setStatus(PayrollStatus.APPROVED);
        run.setApprovedBy(approvedBy);
        run = payrollRunRepository.save(run);

        log.info("Payroll run approved: id={}, by={}", runId, approvedBy);
        return toRunResponse(run);
    }

    /**
     * Rejects a payroll run (only if PENDING_APPROVAL).
     */
    @Transactional
    public PayrollRunResponseDto rejectPayrollRun(UUID runId) {
        PayrollRun run = payrollRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollRun", "id", runId));

        if (run.getStatus() != PayrollStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException(ErrorCode.PAYROLL_LOCKED,
                    "Payroll run is not in PENDING_APPROVAL status: "
                            + run.getStatus());
        }

        run.setStatus(PayrollStatus.REJECTED);
        run = payrollRunRepository.save(run);

        log.info("Payroll run rejected: id={}", runId);
        return toRunResponse(run);
    }

    /**
     * Lists payroll runs for an organization with pagination.
     */
    @Transactional(readOnly = true)
    public PaginatedPayrollRuns listPayrollRuns(UUID organizationId,
                                                 PageRequestDto pageRequest) {
        PageRequest pageable = PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PayrollRun> page = payrollRunRepository
                .findByOrganizationId(organizationId, pageable);

        List<PayrollRunResponseDto> runs = page.getContent().stream()
                .map(this::toRunResponse)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedPayrollRuns(runs, meta);
    }

    // ====================== Payslips ======================

    /**
     * Gets all payslips for a payroll run.
     */
    @Transactional(readOnly = true)
    public List<PayslipResponseDto> getPayslipsForRun(UUID runId) {
        if (!payrollRunRepository.existsById(runId)) {
            throw new ResourceNotFoundException("PayrollRun", "id", runId);
        }
        return payslipRepository.findByPayrollRunId(runId).stream()
                .map(this::toPayslipResponse)
                .collect(Collectors.toList());
    }

    // ====================== Helpers ======================

    /** Computes gross from salary components. */
    private BigDecimal computeGross(CreateSalaryStructureDto dto) {
        BigDecimal gross = dto.getBasic().add(dto.getHra());
        if (dto.getConveyance() != null) {
            gross = gross.add(dto.getConveyance());
        }
        if (dto.getSpecial() != null) {
            gross = gross.add(dto.getSpecial());
        }
        return gross;
    }

    /** Maps SalaryStructure entity to response DTO. */
    private SalaryStructureResponseDto toSalaryResponse(
            SalaryStructure s) {
        return SalaryStructureResponseDto.builder()
                .id(s.getId())
                .employeeId(s.getEmployeeId())
                .basic(s.getBasic())
                .hra(s.getHra())
                .conveyance(s.getConveyance())
                .special(s.getSpecial())
                .grossSalary(s.getGrossSalary())
                .pfEmployee(s.getPfEmployee())
                .pfEmployer(s.getPfEmployer())
                .esicEmployee(s.getEsicEmployee())
                .esicEmployer(s.getEsicEmployer())
                .ptAmount(s.getPtAmount())
                .build();
    }

    /** Maps PayrollRun entity to response DTO. */
    private PayrollRunResponseDto toRunResponse(PayrollRun r) {
        return PayrollRunResponseDto.builder()
                .id(r.getId())
                .month(r.getMonth())
                .year(r.getYear())
                .status(r.getStatus())
                .processedBy(r.getProcessedBy())
                .approvedBy(r.getApprovedBy())
                .bankFileUrl(r.getBankFileUrl())
                .totalGross(r.getTotalGross())
                .totalNet(r.getTotalNet())
                .totalPf(r.getTotalPf())
                .totalEsic(r.getTotalEsic())
                .totalTds(r.getTotalTds())
                .organizationId(r.getOrganizationId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /** Maps Payslip entity to response DTO. */
    private PayslipResponseDto toPayslipResponse(Payslip p) {
        return PayslipResponseDto.builder()
                .id(p.getId())
                .payrollRunId(p.getPayrollRunId())
                .employeeId(p.getEmployeeId())
                .daysWorked(p.getDaysWorked())
                .daysAbsent(p.getDaysAbsent())
                .grossEarnings(p.getGrossEarnings())
                .pfDeduction(p.getPfDeduction())
                .esicDeduction(p.getEsicDeduction())
                .tdsDeduction(p.getTdsDeduction())
                .ptDeduction(p.getPtDeduction())
                .otherDeductions(p.getOtherDeductions())
                .netPayable(p.getNetPayable())
                .build();
    }

    /**
     * Record holding paginated payroll run results.
     */
    public record PaginatedPayrollRuns(List<PayrollRunResponseDto> runs,
                                        PaginationMeta meta) {
    }
}
