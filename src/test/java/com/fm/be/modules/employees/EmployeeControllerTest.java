package com.fm.be.modules.employees;

import com.fm.be.common.constants.DocumentType;
import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.dto.CreateEmployeeDto;
import com.fm.be.modules.employees.dto.EmployeeDocumentDto;
import com.fm.be.modules.employees.dto.EmployeeResponseDto;
import com.fm.be.modules.employees.dto.UpdateEmployeeDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    @InjectMocks
    private EmployeeController employeeController;

    private UserPrincipal adminPrincipal() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();
    }

    @Nested
    @DisplayName("createEmployee")
    class CreateEmployeeTests {

        @Test
        @DisplayName("delegates to EmployeeService.createEmployee")
        void delegatesToService() {
            CreateEmployeeDto dto = mock(CreateEmployeeDto.class);
            EmployeeResponseDto expected = mock(EmployeeResponseDto.class);
            when(employeeService.createEmployee(dto)).thenReturn(expected);

            EmployeeResponseDto result = employeeController.createEmployee(dto);

            verify(employeeService).createEmployee(dto);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("updateEmployee")
    class UpdateEmployeeTests {

        @Test
        @DisplayName("delegates to EmployeeService.updateEmployee")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            UpdateEmployeeDto dto = mock(UpdateEmployeeDto.class);
            EmployeeResponseDto expected = mock(EmployeeResponseDto.class);
            when(employeeService.updateEmployee(id, dto)).thenReturn(expected);

            EmployeeResponseDto result = employeeController.updateEmployee(id, dto);

            verify(employeeService).updateEmployee(id, dto);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getEmployee")
    class GetEmployeeTests {

        @Test
        @DisplayName("delegates to EmployeeService.getEmployeeById")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            EmployeeResponseDto expected = mock(EmployeeResponseDto.class);
            when(employeeService.getEmployeeById(id)).thenReturn(expected);

            EmployeeResponseDto result = employeeController.getEmployee(id);

            verify(employeeService).getEmployeeById(id);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("listEmployees")
    class ListEmployeesTests {

        @Test
        @DisplayName("delegates with organization id from principal and returns paginated ApiResponse")
        void delegatesToService() {
            UserPrincipal principal = adminPrincipal();
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            List<EmployeeResponseDto> employees = List.of(mock(EmployeeResponseDto.class));
            EmployeeService.PaginatedEmployees paginated =
                    new EmployeeService.PaginatedEmployees(employees, meta);

            when(employeeService.listEmployees(principal.getOrganizationId(), pageReq))
                    .thenReturn(paginated);

            ApiResponse<Object> response = employeeController.listEmployees(principal, pageReq);

            verify(employeeService).listEmployees(principal.getOrganizationId(), pageReq);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(employees);
            assertThat(response.getMeta()).isEqualTo(meta);
        }
    }

    @Nested
    @DisplayName("searchEmployees")
    class SearchEmployeesTests {

        @Test
        @DisplayName("delegates with query and organization id and returns paginated ApiResponse")
        void delegatesToService() {
            UserPrincipal principal = adminPrincipal();
            String query = "john";
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            List<EmployeeResponseDto> employees = List.of(mock(EmployeeResponseDto.class));
            EmployeeService.PaginatedEmployees paginated =
                    new EmployeeService.PaginatedEmployees(employees, meta);

            when(employeeService.searchEmployees(query, principal.getOrganizationId(), pageReq))
                    .thenReturn(paginated);

            ApiResponse<Object> response =
                    employeeController.searchEmployees(principal, query, pageReq);

            verify(employeeService).searchEmployees(query, principal.getOrganizationId(), pageReq);
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(employees);
            assertThat(response.getMeta()).isEqualTo(meta);
        }
    }

    @Nested
    @DisplayName("getMyProfile")
    class GetMyProfileTests {

        @Test
        @DisplayName("delegates to EmployeeService.getProfileByUserId with principal user id")
        void delegatesToService() {
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.EMPLOYEE)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            EmployeeResponseDto expected = mock(EmployeeResponseDto.class);
            when(employeeService.getProfileByUserId(userId)).thenReturn(expected);

            EmployeeResponseDto result = employeeController.getMyProfile(principal);

            verify(employeeService).getProfileByUserId(userId);
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("registerFace")
    class RegisterFaceTests {

        @Test
        @DisplayName("delegates to EmployeeService.registerFace and returns success message")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            float[] embedding = new float[] {0.1f, 0.2f};

            Map<String, String> result = employeeController.registerFace(id, embedding);

            verify(employeeService).registerFace(id, embedding);
            assertThat(result).containsEntry("message", "Face registered successfully");
        }
    }

    @Nested
    @DisplayName("addDocument")
    class AddDocumentTests {

        @Test
        @DisplayName("delegates to EmployeeService.addDocument")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            DocumentType type = DocumentType.PAN;
            String fileUrl = "https://storage.example/doc.pdf";
            EmployeeDocumentDto expected = mock(EmployeeDocumentDto.class);
            when(employeeService.addDocument(id, type, fileUrl)).thenReturn(expected);

            EmployeeDocumentDto result = employeeController.addDocument(id, type, fileUrl);

            verify(employeeService).addDocument(id, type, fileUrl);
            assertThat(result).isSameAs(expected);
        }
    }
}
