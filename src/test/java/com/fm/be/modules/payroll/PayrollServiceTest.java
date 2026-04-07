package com.fm.be.modules.payroll;

import com.fm.be.common.constants.ErrorCode;
import com.fm.be.common.constants.PayrollStatus;
import com.fm.be.common.exception.BusinessRuleException;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.payroll.dto.CreateSalaryStructureDto;
import com.fm.be.modules.payroll.dto.PayrollRunResponseDto;
import com.fm.be.modules.payroll.dto.PayslipResponseDto;
import com.fm.be.modules.payroll.dto.SalaryStructureResponseDto;
import com.fm.be.modules.payroll.entity.PayrollRun;
import com.fm.be.modules.payroll.entity.Payslip;
import com.fm.be.modules.payroll.entity.SalaryStructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fm.be.common.dto.PageRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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
 * Unit tests for {@link PayrollService}.
 * Covers salary structure CRUD, payroll run lifecycle, and payslip queries.
 */
@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private SalaryStructureRepository salaryStructureRepository;
    @Mock
    private PayrollRunRepository payrollRunRepository;
    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PayrollCalculationService calculationService;
    @Mock
    private com.fm.be.scheduler.PayrollJobScheduler payrollJobScheduler;

    @InjectMocks
    private PayrollService payrollService;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("createSalaryStructure")
    class CreateSalaryStructureTests {

        @Test
        @DisplayName("should create structure with computed deductions")
        void createSalaryStructure_validData_creates() {
            when(employeeRepository.existsById(employeeId))
                    .thenReturn(true);
            when(salaryStructureRepository.existsByEmployeeId(employeeId))
                    .thenReturn(false);
            when(calculationService.calculatePfEmployee(any()))
                    .thenReturn(new BigDecimal("1800.00"));
            when(calculationService.calculatePfEmployer(any()))
                    .thenReturn(new BigDecimal("1800.00"));
            when(calculationService.calculateEsicEmployee(any()))
                    .thenReturn(BigDecimal.ZERO);
            when(calculationService.calculateEsicEmployer(any()))
                    .thenReturn(BigDecimal.ZERO);
            when(calculationService.calculatePt(any(), any(int.class)))
                    .thenReturn(new BigDecimal("200.00"));
            when(salaryStructureRepository.save(any(SalaryStructure.class)))
                    .thenAnswer(i -> {
                        SalaryStructure s = i.getArgument(0);
                        s.setId(UUID.randomUUID());
                        s.setCreatedAt(Instant.now());
                        s.setUpdatedAt(Instant.now());
                        return s;
                    });

            CreateSalaryStructureDto dto = CreateSalaryStructureDto.builder()
                    .employeeId(employeeId)
                    .basic(new BigDecimal("20000"))
                    .hra(new BigDecimal("8000"))
                    .conveyance(new BigDecimal("2000"))
                    .build();

            SalaryStructureResponseDto result =
                    payrollService.createSalaryStructure(dto);

            assertThat(result.getGrossSalary())
                    .isEqualByComparingTo(new BigDecimal("30000"));
            assertThat(result.getPfEmployee())
                    .isEqualByComparingTo(new BigDecimal("1800.00"));
        }

        @Test
        @DisplayName("should throw on duplicate employee structure")
        void createSalaryStructure_duplicate_throws() {
            when(employeeRepository.existsById(employeeId))
                    .thenReturn(true);
            when(salaryStructureRepository.existsByEmployeeId(employeeId))
                    .thenReturn(true);

            CreateSalaryStructureDto dto = CreateSalaryStructureDto.builder()
                    .employeeId(employeeId)
                    .basic(new BigDecimal("20000"))
                    .hra(new BigDecimal("8000"))
                    .build();

            assertThatThrownBy(() ->
                    payrollService.createSalaryStructure(dto))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("initiatePayrollRun")
    class InitiatePayrollRunTests {

        @Test
        @DisplayName("should create run with DRAFT status")
        void initiatePayrollRun_validPeriod_createsDraft() {
            when(payrollRunRepository.existsByMonthAndYearAndOrganizationId(
                    4, 2026, orgId)).thenReturn(false);
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> {
                        PayrollRun r = i.getArgument(0);
                        r.setId(UUID.randomUUID());
                        r.setCreatedAt(Instant.now());
                        r.setUpdatedAt(Instant.now());
                        return r;
                    });

            PayrollRunResponseDto result =
                    payrollService.initiatePayrollRun(4, 2026, orgId, userId);

            assertThat(result.getStatus()).isEqualTo(PayrollStatus.DRAFT);
            assertThat(result.getMonth()).isEqualTo(4);
            assertThat(result.getYear()).isEqualTo(2026);
        }

        @Test
        @DisplayName("should throw on duplicate period")
        void initiatePayrollRun_duplicatePeriod_throws() {
            when(payrollRunRepository.existsByMonthAndYearAndOrganizationId(
                    4, 2026, orgId)).thenReturn(true);

            assertThatThrownBy(() ->
                    payrollService.initiatePayrollRun(4, 2026, orgId, userId))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("approvePayrollRun")
    class ApprovePayrollRunTests {

        @Test
        @DisplayName("should approve run in PENDING_APPROVAL status")
        void approvePayrollRun_pendingApproval_approves() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = PayrollRun.builder()
                    .id(runId)
                    .month(4)
                    .year(2026)
                    .status(PayrollStatus.PENDING_APPROVAL)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));

            PayrollRunResponseDto result =
                    payrollService.approvePayrollRun(runId, userId);

            assertThat(result.getStatus()).isEqualTo(PayrollStatus.APPROVED);
            assertThat(result.getApprovedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should throw PAYROLL_LOCKED when not in PENDING")
        void approvePayrollRun_wrongStatus_throws() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = PayrollRun.builder()
                    .id(runId)
                    .status(PayrollStatus.DRAFT)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));

            assertThatThrownBy(() ->
                    payrollService.approvePayrollRun(runId, userId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            ErrorCode.PAYROLL_LOCKED);
        }
    }

    @Nested
    @DisplayName("rejectPayrollRun")
    class RejectPayrollRunTests {

        @Test
        @DisplayName("should reject run in PENDING_APPROVAL status")
        void rejectPayrollRun_pendingApproval_rejects() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = PayrollRun.builder()
                    .id(runId)
                    .status(PayrollStatus.PENDING_APPROVAL)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));
            when(payrollRunRepository.save(any(PayrollRun.class)))
                    .thenAnswer(i -> i.getArgument(0));

            PayrollRunResponseDto result =
                    payrollService.rejectPayrollRun(runId);

            assertThat(result.getStatus()).isEqualTo(PayrollStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("getPayslipsForRun")
    class PayslipTests {

        @Test
        @DisplayName("should return payslips for a run")
        void getPayslipsForRun_returnsAll() {
            UUID runId = UUID.randomUUID();
            when(payrollRunRepository.existsById(runId)).thenReturn(true);

            Payslip payslip = Payslip.builder()
                    .id(UUID.randomUUID())
                    .payrollRunId(runId)
                    .employeeId(employeeId)
                    .grossEarnings(new BigDecimal("30000"))
                    .pfDeduction(new BigDecimal("1800"))
                    .netPayable(new BigDecimal("28000"))
                    .build();
            when(payslipRepository.findByPayrollRunId(runId))
                    .thenReturn(List.of(payslip));

            List<PayslipResponseDto> result =
                    payrollService.getPayslipsForRun(runId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getGrossEarnings())
                    .isEqualByComparingTo(new BigDecimal("30000"));
        }

        @Test
        @DisplayName("should throw when run not found")
        void getPayslipsForRun_runNotFound_throws() {
            UUID runId = UUID.randomUUID();
            when(payrollRunRepository.existsById(runId)).thenReturn(false);

            assertThatThrownBy(() ->
                    payrollService.getPayslipsForRun(runId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getSalaryStructure")
    class GetSalaryStructureTests {

        @Test
        @DisplayName("should return structure for existing employee")
        void getSalaryStructure_found_returnsDto() {
            SalaryStructure structure = SalaryStructure.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .basic(new BigDecimal("20000"))
                    .hra(new BigDecimal("8000"))
                    .grossSalary(new BigDecimal("28000"))
                    .pfEmployee(new BigDecimal("1800"))
                    .pfEmployer(new BigDecimal("1800"))
                    .esicEmployee(BigDecimal.ZERO)
                    .esicEmployer(BigDecimal.ZERO)
                    .ptAmount(new BigDecimal("200"))
                    .build();

            when(salaryStructureRepository.findByEmployeeId(employeeId))
                    .thenReturn(Optional.of(structure));

            SalaryStructureResponseDto result =
                    payrollService.getSalaryStructure(employeeId);

            assertThat(result.getEmployeeId()).isEqualTo(employeeId);
            assertThat(result.getGrossSalary())
                    .isEqualByComparingTo(new BigDecimal("28000"));
        }

        @Test
        @DisplayName("should throw when structure not found")
        void getSalaryStructure_notFound_throws() {
            when(salaryStructureRepository.findByEmployeeId(employeeId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    payrollService.getSalaryStructure(employeeId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateSalaryStructure")
    class UpdateSalaryStructureTests {

        @Test
        @DisplayName("should update structure and recalculate deductions")
        void updateSalaryStructure_valid_updatesAndRecalculates() {
            SalaryStructure existing = SalaryStructure.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .basic(new BigDecimal("20000"))
                    .hra(new BigDecimal("8000"))
                    .grossSalary(new BigDecimal("28000"))
                    .build();

            when(salaryStructureRepository.findByEmployeeId(employeeId))
                    .thenReturn(Optional.of(existing));
            when(calculationService.calculatePfEmployee(any()))
                    .thenReturn(new BigDecimal("2400"));
            when(calculationService.calculatePfEmployer(any()))
                    .thenReturn(new BigDecimal("2400"));
            when(calculationService.calculateEsicEmployee(any()))
                    .thenReturn(BigDecimal.ZERO);
            when(calculationService.calculateEsicEmployer(any()))
                    .thenReturn(BigDecimal.ZERO);
            when(calculationService.calculatePt(any(), eq(1)))
                    .thenReturn(new BigDecimal("200"));
            when(salaryStructureRepository.save(any(SalaryStructure.class)))
                    .thenAnswer(i -> i.getArgument(0));

            CreateSalaryStructureDto dto = CreateSalaryStructureDto.builder()
                    .employeeId(employeeId)
                    .basic(new BigDecimal("25000"))
                    .hra(new BigDecimal("10000"))
                    .conveyance(new BigDecimal("3000"))
                    .special(new BigDecimal("2000"))
                    .build();

            SalaryStructureResponseDto result =
                    payrollService.updateSalaryStructure(employeeId, dto);

            assertThat(result.getGrossSalary())
                    .isEqualByComparingTo(new BigDecimal("40000"));
            assertThat(result.getPfEmployee())
                    .isEqualByComparingTo(new BigDecimal("2400"));
        }

        @Test
        @DisplayName("should throw when employee structure not found")
        void updateSalaryStructure_notFound_throws() {
            when(salaryStructureRepository.findByEmployeeId(employeeId))
                    .thenReturn(Optional.empty());

            CreateSalaryStructureDto dto = CreateSalaryStructureDto.builder()
                    .employeeId(employeeId)
                    .basic(new BigDecimal("25000"))
                    .hra(new BigDecimal("10000"))
                    .build();

            assertThatThrownBy(() ->
                    payrollService.updateSalaryStructure(employeeId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPayrollRunStatus")
    class PayrollRunStatusTests {

        @Test
        @DisplayName("should return status for existing run")
        void getPayrollRunStatus_found_returnsDto() {
            UUID runId = UUID.randomUUID();
            PayrollRun run = PayrollRun.builder()
                    .id(runId)
                    .month(3)
                    .year(2026)
                    .status(PayrollStatus.PROCESSING)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.of(run));

            PayrollRunResponseDto result =
                    payrollService.getPayrollRunStatus(runId);

            assertThat(result.getStatus()).isEqualTo(PayrollStatus.PROCESSING);
            assertThat(result.getMonth()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw when run not found")
        void getPayrollRunStatus_notFound_throws() {
            UUID runId = UUID.randomUUID();
            when(payrollRunRepository.findById(runId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    payrollService.getPayrollRunStatus(runId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listPayrollRuns")
    class ListPayrollRunsTests {

        @Test
        @DisplayName("should return paginated runs for organization")
        void listPayrollRuns_returnsPaginated() {
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();

            PayrollRun run = PayrollRun.builder()
                    .id(UUID.randomUUID())
                    .month(3)
                    .year(2026)
                    .status(PayrollStatus.APPROVED)
                    .organizationId(orgId)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            Page<PayrollRun> page = new PageImpl<>(
                    List.of(run), PageRequest.of(0, 10), 1);
            when(payrollRunRepository.findByOrganizationId(
                    eq(orgId), any(PageRequest.class)))
                    .thenReturn(page);

            PayrollService.PaginatedPayrollRuns result =
                    payrollService.listPayrollRuns(orgId, pageReq);

            assertThat(result.runs()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }
}
