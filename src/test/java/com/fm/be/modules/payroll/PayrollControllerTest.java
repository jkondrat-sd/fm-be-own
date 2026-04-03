package com.fm.be.modules.payroll;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.payroll.dto.CreateSalaryStructureDto;
import com.fm.be.modules.payroll.dto.PayrollRunResponseDto;
import com.fm.be.modules.payroll.dto.PayslipResponseDto;
import com.fm.be.modules.payroll.dto.SalaryStructureResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollControllerTest {

    @Mock
    private PayrollService payrollService;

    @InjectMocks
    private PayrollController payrollController;

    @Nested
    @DisplayName("Salary structure endpoints")
    class SalaryStructureEndpoints {

        @Test
        @DisplayName("createSalaryStructure delegates to payrollService and returns response")
        void createSalaryStructure_delegatesToService() {
            CreateSalaryStructureDto dto = mock(CreateSalaryStructureDto.class);
            SalaryStructureResponseDto expected = mock(SalaryStructureResponseDto.class);
            when(payrollService.createSalaryStructure(dto)).thenReturn(expected);

            SalaryStructureResponseDto result = payrollController.createSalaryStructure(dto);

            assertThat(result).isSameAs(expected);
            verify(payrollService).createSalaryStructure(dto);
        }

        @Test
        @DisplayName("getSalaryStructure delegates to payrollService with employeeId")
        void getSalaryStructure_delegatesToService() {
            UUID employeeId = UUID.randomUUID();
            SalaryStructureResponseDto expected = mock(SalaryStructureResponseDto.class);
            when(payrollService.getSalaryStructure(employeeId)).thenReturn(expected);

            SalaryStructureResponseDto result = payrollController.getSalaryStructure(employeeId);

            assertThat(result).isSameAs(expected);
            verify(payrollService).getSalaryStructure(employeeId);
        }

        @Test
        @DisplayName("updateSalaryStructure delegates to payrollService")
        void updateSalaryStructure_delegatesToService() {
            UUID employeeId = UUID.randomUUID();
            CreateSalaryStructureDto dto = mock(CreateSalaryStructureDto.class);
            SalaryStructureResponseDto expected = mock(SalaryStructureResponseDto.class);
            when(payrollService.updateSalaryStructure(employeeId, dto)).thenReturn(expected);

            SalaryStructureResponseDto result =
                    payrollController.updateSalaryStructure(employeeId, dto);

            assertThat(result).isSameAs(expected);
            verify(payrollService).updateSalaryStructure(employeeId, dto);
        }
    }

    @Nested
    @DisplayName("Payroll run endpoints")
    class PayrollRunEndpoints {

        @Test
        @DisplayName("initiatePayrollRun passes month, year, organizationId and userId from principal")
        void initiatePayrollRun_delegatesWithPrincipalContext() {
            UUID orgId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.ADMIN)
                    .organizationId(orgId)
                    .siteIds(List.of())
                    .build();
            int month = 4;
            int year = 2026;
            PayrollRunResponseDto expected = mock(PayrollRunResponseDto.class);
            when(payrollService.initiatePayrollRun(month, year, orgId, userId))
                    .thenReturn(expected);

            PayrollRunResponseDto result =
                    payrollController.initiatePayrollRun(principal, month, year);

            assertThat(result).isSameAs(expected);
            verify(payrollService).initiatePayrollRun(month, year, orgId, userId);
        }

        @Test
        @DisplayName("getRunStatus delegates to getPayrollRunStatus")
        void getRunStatus_delegatesToService() {
            UUID id = UUID.randomUUID();
            PayrollRunResponseDto expected = mock(PayrollRunResponseDto.class);
            when(payrollService.getPayrollRunStatus(id)).thenReturn(expected);

            PayrollRunResponseDto result = payrollController.getRunStatus(id);

            assertThat(result).isSameAs(expected);
            verify(payrollService).getPayrollRunStatus(id);
        }

        @Test
        @DisplayName("approveRun delegates with run id and principal userId")
        void approveRun_delegatesToService() {
            UUID id = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(actorId)
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            PayrollRunResponseDto expected = mock(PayrollRunResponseDto.class);
            when(payrollService.approvePayrollRun(id, actorId)).thenReturn(expected);

            PayrollRunResponseDto result = payrollController.approveRun(id, principal);

            assertThat(result).isSameAs(expected);
            verify(payrollService).approvePayrollRun(id, actorId);
        }

        @Test
        @DisplayName("rejectRun delegates to rejectPayrollRun")
        void rejectRun_delegatesToService() {
            UUID id = UUID.randomUUID();
            PayrollRunResponseDto expected = mock(PayrollRunResponseDto.class);
            when(payrollService.rejectPayrollRun(id)).thenReturn(expected);

            PayrollRunResponseDto result = payrollController.rejectRun(id);

            assertThat(result).isSameAs(expected);
            verify(payrollService).rejectPayrollRun(id);
        }

        @Test
        @DisplayName("listRuns wraps paginated runs in ApiResponse")
        void listRuns_returnsApiResponsePaginated() {
            UUID orgId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(UUID.randomUUID())
                    .role(Role.ADMIN)
                    .organizationId(orgId)
                    .siteIds(List.of())
                    .build();
            PageRequestDto pageRequest = mock(PageRequestDto.class);
            PayrollRunResponseDto run = mock(PayrollRunResponseDto.class);
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            PayrollService.PaginatedPayrollRuns paginated =
                    new PayrollService.PaginatedPayrollRuns(List.of(run), meta);
            when(payrollService.listPayrollRuns(orgId, pageRequest)).thenReturn(paginated);

            ApiResponse<Object> response = payrollController.listRuns(principal, pageRequest);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(paginated.runs());
            assertThat(response.getMeta()).isEqualTo(paginated.meta());
            verify(payrollService).listPayrollRuns(eq(orgId), eq(pageRequest));
        }
    }

    @Nested
    @DisplayName("Payslips")
    class Payslips {

        @Test
        @DisplayName("getPayslips delegates to getPayslipsForRun")
        void getPayslips_delegatesToService() {
            UUID id = UUID.randomUUID();
            PayslipResponseDto slip = mock(PayslipResponseDto.class);
            List<PayslipResponseDto> expected = List.of(slip);
            when(payrollService.getPayslipsForRun(id)).thenReturn(expected);

            List<PayslipResponseDto> result = payrollController.getPayslips(id);

            assertThat(result).isSameAs(expected);
            verify(payrollService).getPayslipsForRun(id);
        }
    }
}
