package com.fm.be.modules.admin;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.admin.dto.AuditLogResponseDto;
import com.fm.be.modules.admin.dto.SystemConfigDto;
import com.fm.be.modules.admin.dto.SystemStatsDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @Nested
    @DisplayName("getAuditLogs")
    class GetAuditLogs {

        @Test
        @DisplayName("delegates to adminService and returns paginated ApiResponse")
        void getAuditLogs_wrapsPaginatedResult() {
            UUID userId = UUID.randomUUID();
            String action = "CREATE_USER";
            PageRequestDto pageRequest = mock(PageRequestDto.class);
            AuditLogResponseDto log = mock(AuditLogResponseDto.class);
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            AdminService.PaginatedAuditLogs paginated =
                    new AdminService.PaginatedAuditLogs(List.of(log), meta);
            when(adminService.getAuditLogs(userId, action, pageRequest)).thenReturn(paginated);

            ApiResponse<Object> response =
                    adminController.getAuditLogs(userId, action, pageRequest);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(paginated.logs());
            assertThat(response.getMeta()).isEqualTo(paginated.meta());
            verify(adminService).getAuditLogs(eq(userId), eq(action), eq(pageRequest));
        }
    }

    @Nested
    @DisplayName("getDashboardStats")
    class GetDashboardStats {

        @Test
        @DisplayName("delegates to adminService")
        void getDashboardStats_delegatesToService() {
            SystemStatsDto expected = mock(SystemStatsDto.class);
            when(adminService.getDashboardStats()).thenReturn(expected);

            SystemStatsDto result = adminController.getDashboardStats();

            assertThat(result).isSameAs(expected);
            verify(adminService).getDashboardStats();
        }
    }

    @Nested
    @DisplayName("exportData")
    class ExportData {

        @Test
        @DisplayName("calls exportData with userId and remote address then returns CSV ResponseEntity")
        void exportData_returnsCsvWithDisposition() {
            String entity = "users";
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            String csv = "id,name\n1,Alice\n";
            when(adminService.exportData(entity, userId, "127.0.0.1")).thenReturn(csv);

            ResponseEntity<byte[]> response =
                    adminController.exportData(entity, principal, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
            assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                    .isEqualTo("attachment; filename=" + entity + "_export.csv");
            assertThat(response.getBody()).isEqualTo(csv.getBytes());
            verify(adminService).exportData(entity, userId, "127.0.0.1");
        }
    }

    @Nested
    @DisplayName("System configuration")
    class SystemConfiguration {

        @Test
        @DisplayName("getSystemConfig delegates to adminService")
        void getSystemConfig_delegatesToService() {
            SystemConfigDto expected = mock(SystemConfigDto.class);
            when(adminService.getSystemConfig()).thenReturn(expected);

            SystemConfigDto result = adminController.getSystemConfig();

            assertThat(result).isSameAs(expected);
            verify(adminService).getSystemConfig();
        }

        @Test
        @DisplayName("updateSystemConfig passes dto, userId and remote address")
        void updateSystemConfig_delegatesToService() {
            SystemConfigDto dto = mock(SystemConfigDto.class);
            UUID userId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(userId)
                    .role(Role.SUPER_ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            SystemConfigDto updated = mock(SystemConfigDto.class);
            when(adminService.updateSystemConfig(dto, userId, "127.0.0.1"))
                    .thenReturn(updated);

            SystemConfigDto result =
                    adminController.updateSystemConfig(dto, principal, request);

            assertThat(result).isSameAs(updated);
            verify(adminService).updateSystemConfig(dto, userId, "127.0.0.1");
        }
    }
}
