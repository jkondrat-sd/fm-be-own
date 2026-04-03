package com.fm.be.modules.siteupdates;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.siteupdates.dto.CreateSiteUpdateDto;
import com.fm.be.modules.siteupdates.dto.SiteUpdateResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteUpdateControllerTest {

    @Mock
    private SiteUpdateService siteUpdateService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private SiteUpdateController siteUpdateController;

    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    private UserPrincipal buildPrincipal() {
        return UserPrincipal.builder()
                .userId(userId)
                .role(Role.EMPLOYEE)
                .organizationId(orgId)
                .siteIds(List.of())
                .build();
    }

    @Nested
    @DisplayName("createSiteUpdate")
    class CreateSiteUpdateTests {

        @Test
        @DisplayName("should look up employee and delegate to service")
        void createSiteUpdate_delegatesToService() {
            UserPrincipal principal = buildPrincipal();
            Employee employee = Employee.builder().id(employeeId).build();
            when(employeeRepository.findByUserId(userId))
                    .thenReturn(Optional.of(employee));

            CreateSiteUpdateDto dto = mock(CreateSiteUpdateDto.class);
            SiteUpdateResponseDto expected = mock(SiteUpdateResponseDto.class);
            when(siteUpdateService.createSiteUpdate(employeeId, dto))
                    .thenReturn(expected);

            SiteUpdateResponseDto result =
                    siteUpdateController.createSiteUpdate(principal, dto);

            assertThat(result).isSameAs(expected);
            verify(employeeRepository).findByUserId(userId);
            verify(siteUpdateService).createSiteUpdate(employeeId, dto);
        }
    }

    @Nested
    @DisplayName("listBySite")
    class ListBySiteTests {

        @Test
        @DisplayName("should return paginated site updates for site")
        void listBySite_returnsPaginated() {
            UUID siteId = UUID.randomUUID();
            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(10).build();
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1).limit(10).total(1).totalPages(1).build();
            SiteUpdateResponseDto update = mock(SiteUpdateResponseDto.class);

            when(siteUpdateService.listBySite(siteId, pageReq))
                    .thenReturn(new SiteUpdateService.PaginatedSiteUpdates(
                            List.of(update), meta));

            ApiResponse<Object> result =
                    siteUpdateController.listBySite(siteId, pageReq);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMeta()).isEqualTo(meta);
        }
    }

    @Nested
    @DisplayName("getSiteUpdate")
    class GetSiteUpdateTests {

        @Test
        @DisplayName("should delegate to service by id")
        void getSiteUpdate_delegatesToService() {
            UUID id = UUID.randomUUID();
            SiteUpdateResponseDto expected = mock(SiteUpdateResponseDto.class);
            when(siteUpdateService.getById(id)).thenReturn(expected);

            SiteUpdateResponseDto result =
                    siteUpdateController.getSiteUpdate(id);

            assertThat(result).isSameAs(expected);
            verify(siteUpdateService).getById(id);
        }
    }
}
