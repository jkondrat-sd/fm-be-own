package com.fm.be.modules.sites;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.sites.dto.CreateSiteDto;
import com.fm.be.modules.sites.dto.GeofenceDto;
import com.fm.be.modules.sites.dto.SiteResponseDto;
import com.fm.be.modules.sites.dto.UpdateSiteDto;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteControllerTest {

    @Mock
    private SiteService siteService;

    @InjectMocks
    private SiteController siteController;

    private UserPrincipal principal() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();
    }

    @Nested
    class CreateSite {

        @Test
        @DisplayName("delegates to siteService.createSite and returns SiteResponseDto")
        void delegatesToService() {
            CreateSiteDto dto = mock(CreateSiteDto.class);
            SiteResponseDto response = mock(SiteResponseDto.class);
            when(siteService.createSite(dto)).thenReturn(response);

            SiteResponseDto result = siteController.createSite(dto);

            assertThat(result).isSameAs(response);
            verify(siteService).createSite(dto);
        }
    }

    @Nested
    class UpdateSite {

        @Test
        @DisplayName("calls siteService.updateSite with organization id from principal")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            UserPrincipal principal = principal();
            UpdateSiteDto dto = mock(UpdateSiteDto.class);
            SiteResponseDto response = mock(SiteResponseDto.class);
            when(siteService.updateSite(id, principal.getOrganizationId(), dto))
                    .thenReturn(response);

            SiteResponseDto result = siteController.updateSite(id, principal, dto);

            assertThat(result).isSameAs(response);
            verify(siteService).updateSite(id, principal.getOrganizationId(), dto);
        }
    }

    @Nested
    class GetSite {

        @Test
        @DisplayName("calls siteService.getSiteById with organization id from principal")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            UserPrincipal principal = principal();
            SiteResponseDto response = mock(SiteResponseDto.class);
            when(siteService.getSiteById(id, principal.getOrganizationId())).thenReturn(response);

            SiteResponseDto result = siteController.getSite(id, principal);

            assertThat(result).isSameAs(response);
            verify(siteService).getSiteById(id, principal.getOrganizationId());
        }
    }

    @Nested
    class ListSites {

        @Test
        @DisplayName("calls siteService.listSites and returns ApiResponse.paginated")
        void returnsPaginatedResponse() {
            UserPrincipal principal = principal();
            PageRequestDto pageReq = PageRequestDto.builder().page(1).limit(10).build();
            SiteResponseDto site = mock(SiteResponseDto.class);
            List<SiteResponseDto> sites = List.of(site);
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            when(siteService.listSites(principal.getOrganizationId(), pageReq))
                    .thenReturn(new SiteService.PaginatedSites(sites, meta));

            ApiResponse<Object> response = siteController.listSites(principal, pageReq);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(sites);
            assertThat(response.getMeta()).isEqualTo(meta);
            verify(siteService).listSites(principal.getOrganizationId(), pageReq);
        }
    }

    @Nested
    class SetGeofence {

        @Test
        @DisplayName("calls siteService.setGeofence and returns success message map")
        void delegatesAndReturnsMessage() {
            UUID id = UUID.randomUUID();
            UserPrincipal principal = principal();
            GeofenceDto dto = mock(GeofenceDto.class);

            Map<String, String> result = siteController.setGeofence(id, principal, dto);

            assertThat(result).containsEntry("message", "Geofence updated successfully");
            verify(siteService).setGeofence(id, principal.getOrganizationId(), dto);
        }
    }

    @Nested
    class DeactivateSite {

        @Test
        @DisplayName("calls siteService.deactivateSite with organization id from principal")
        void delegatesToService() {
            UUID id = UUID.randomUUID();
            UserPrincipal principal = principal();

            siteController.deactivateSite(id, principal);

            verify(siteService).deactivateSite(eq(id), eq(principal.getOrganizationId()));
        }
    }
}
