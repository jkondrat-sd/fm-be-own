package com.fm.be.modules.sites;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.sites.dto.CreateSiteDto;
import com.fm.be.modules.sites.dto.GeofenceDto;
import com.fm.be.modules.sites.dto.SiteResponseDto;
import com.fm.be.modules.sites.dto.UpdateSiteDto;
import com.fm.be.modules.sites.entity.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SiteService}.
 * Covers CRUD, geofence management, soft delete, and cache invalidation.
 */
@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private SiteService siteService;

    private Site testSite;
    private final UUID siteId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testSite = Site.builder()
                .id(siteId)
                .name("Test Site")
                .address("123 Main St")
                .latitude(19.0760)
                .longitude(72.8777)
                .geofenceRadius(100.0)
                .isActive(true)
                .clientId(clientId)
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createSite")
    class CreateSiteTests {

        @Test
        @DisplayName("should create site without geofence")
        void createSite_noGeofence_createsSite() {
            when(siteRepository.save(any(Site.class))).thenAnswer(i -> {
                Site s = i.getArgument(0);
                s.setId(UUID.randomUUID());
                s.setCreatedAt(Instant.now());
                s.setUpdatedAt(Instant.now());
                return s;
            });

            CreateSiteDto dto = CreateSiteDto.builder()
                    .name("New Site")
                    .address("456 Oak Ave")
                    .organizationId(orgId)
                    .build();

            SiteResponseDto result = siteService.createSite(dto);

            assertThat(result.getName()).isEqualTo("New Site");
            assertThat(result.getAddress()).isEqualTo("456 Oak Ave");
            verify(siteRepository, never()).setCircularGeofence(
                    any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("should create site with circular geofence")
        void createSite_withGeofence_setsGeofence() {
            when(siteRepository.save(any(Site.class))).thenAnswer(i -> {
                Site s = i.getArgument(0);
                s.setId(UUID.randomUUID());
                s.setCreatedAt(Instant.now());
                s.setUpdatedAt(Instant.now());
                return s;
            });

            CreateSiteDto dto = CreateSiteDto.builder()
                    .name("Geofenced Site")
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .geofenceRadius(200.0)
                    .organizationId(orgId)
                    .build();

            SiteResponseDto result = siteService.createSite(dto);

            assertThat(result.getName()).isEqualTo("Geofenced Site");
            verify(siteRepository).setCircularGeofence(
                    any(UUID.class), eq(19.0760), eq(72.8777), eq(200.0));
        }
    }

    @Nested
    @DisplayName("updateSite")
    class UpdateSiteTests {

        @Test
        @DisplayName("should update site name and address")
        void updateSite_validFields_updates() {
            when(siteRepository.findByIdAndOrganizationId(siteId, orgId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.save(any(Site.class)))
                    .thenAnswer(i -> i.getArgument(0));

            UpdateSiteDto dto = UpdateSiteDto.builder()
                    .name("Updated Site")
                    .address("789 Elm St")
                    .build();

            SiteResponseDto result = siteService.updateSite(siteId, orgId, dto);

            assertThat(result.getName()).isEqualTo("Updated Site");
            assertThat(result.getAddress()).isEqualTo("789 Elm St");
        }

        @Test
        @DisplayName("should throw when site not found")
        void updateSite_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.findByIdAndOrganizationId(randomId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> siteService.updateSite(
                    randomId, orgId, new UpdateSiteDto()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getSiteById")
    class GetSiteByIdTests {

        @Test
        @DisplayName("should return site when found")
        void getSiteById_found_returnsDto() {
            when(siteRepository.findByIdAndOrganizationId(siteId, orgId))
                    .thenReturn(Optional.of(testSite));

            SiteResponseDto result = siteService.getSiteById(siteId, orgId);

            assertThat(result.getName()).isEqualTo("Test Site");
            assertThat(result.getLatitude()).isEqualTo(19.0760);
        }

        @Test
        @DisplayName("should throw when site not found")
        void getSiteById_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.findByIdAndOrganizationId(randomId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> siteService.getSiteById(randomId, orgId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listSites")
    class ListSitesTests {

        @Test
        @DisplayName("should return paginated sites")
        void listSites_returnsPaginated() {
            Page<Site> page = new PageImpl<>(List.of(testSite));
            when(siteRepository.findByOrganizationId(
                    eq(orgId), any(Pageable.class))).thenReturn(page);

            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            SiteService.PaginatedSites result =
                    siteService.listSites(orgId, pageReq);

            assertThat(result.sites()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("setGeofence")
    class SetGeofenceTests {

        @Test
        @DisplayName("should set circular geofence and invalidate cache")
        void setGeofence_circular_setsAndInvalidates() {
            when(siteRepository.findByIdAndOrganizationId(siteId, orgId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.save(any(Site.class)))
                    .thenAnswer(i -> i.getArgument(0));

            GeofenceDto dto = GeofenceDto.builder()
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .radiusMeters(300.0)
                    .build();

            siteService.setGeofence(siteId, orgId, dto);

            verify(siteRepository).setCircularGeofence(
                    siteId, 19.0760, 72.8777, 300.0);
            verify(redisTemplate).delete(
                    String.format("site:%s:geofence", siteId));
        }

        @Test
        @DisplayName("should set polygon geofence from GeoJSON")
        void setGeofence_polygon_setsAndInvalidates() {
            when(siteRepository.findByIdAndOrganizationId(siteId, orgId))
                    .thenReturn(Optional.of(testSite));

            String geoJson = "{\"type\":\"Polygon\",\"coordinates\":"
                    + "[[[72.87,19.07],[72.88,19.07],[72.88,19.08],"
                    + "[72.87,19.08],[72.87,19.07]]]}";

            GeofenceDto dto = GeofenceDto.builder()
                    .latitude(19.0760)
                    .longitude(72.8777)
                    .geoJson(geoJson)
                    .build();

            siteService.setGeofence(siteId, orgId, dto);

            verify(siteRepository).setPolygonGeofence(siteId, geoJson);
            verify(siteRepository, never()).setCircularGeofence(
                    any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("should throw when site not found")
        void setGeofence_siteNotFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.findByIdAndOrganizationId(randomId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> siteService.setGeofence(
                    randomId, orgId, new GeofenceDto()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deactivateSite")
    class DeactivateSiteTests {

        @Test
        @DisplayName("should set isActive to false")
        void deactivateSite_setsInactive() {
            when(siteRepository.findByIdAndOrganizationId(siteId, orgId))
                    .thenReturn(Optional.of(testSite));
            when(siteRepository.save(any(Site.class)))
                    .thenAnswer(i -> i.getArgument(0));

            siteService.deactivateSite(siteId, orgId);

            ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
            verify(siteRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("should throw when site not found")
        void deactivateSite_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteRepository.findByIdAndOrganizationId(randomId, orgId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> siteService.deactivateSite(randomId, orgId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
