package com.fm.be.modules.location;

import com.fm.be.common.constants.Role;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.location.dto.GeofenceAlertDto;
import com.fm.be.modules.location.dto.HeatmapDto;
import com.fm.be.modules.location.dto.LiveLocationDto;
import com.fm.be.modules.location.dto.LocationUpdateDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

    @Mock
    private LocationService locationService;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LocationController locationController;

    private UserPrincipal principal() {
        return UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.EMPLOYEE)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();
    }

    @Nested
    class UpdateLocation {

        @Test
        @DisplayName("resolves employee by user id then calls locationService.handleLocationUpdate")
        void looksUpEmployeeAndDelegates() {
            UserPrincipal principal = principal();
            LocationUpdateDto dto = mock(LocationUpdateDto.class);
            Employee employee = Employee.builder().id(UUID.randomUUID()).build();
            when(employeeRepository.findByUserId(principal.getUserId()))
                    .thenReturn(Optional.of(employee));

            Map<String, String> result = locationController.updateLocation(principal, dto);

            assertThat(result).containsEntry("status", "ok");
            verify(employeeRepository).findByUserId(principal.getUserId());
            verify(locationService).handleLocationUpdate(employee.getId(), dto);
        }
    }

    @Nested
    class GetLiveLocations {

        @Test
        @DisplayName("delegates to locationService.getLiveLocations")
        void delegatesToService() {
            UUID siteId = UUID.randomUUID();
            LiveLocationDto live = mock(LiveLocationDto.class);
            List<LiveLocationDto> expected = List.of(live);
            when(locationService.getLiveLocations(siteId)).thenReturn(expected);

            List<LiveLocationDto> result = locationController.getLiveLocations(siteId);

            assertThat(result).isSameAs(expected);
            verify(locationService).getLiveLocations(siteId);
        }
    }

    @Nested
    class GetHeatmapData {

        @Test
        @DisplayName("delegates to locationService.getHeatmapData with site and time range")
        void delegatesToService() {
            UUID siteId = UUID.randomUUID();
            Instant start = Instant.parse("2026-04-01T00:00:00Z");
            Instant end = Instant.parse("2026-04-01T23:59:59Z");
            HeatmapDto heatmap = mock(HeatmapDto.class);
            when(locationService.getHeatmapData(siteId, start, end)).thenReturn(heatmap);

            HeatmapDto result = locationController.getHeatmapData(siteId, start, end);

            assertThat(result).isSameAs(heatmap);
            verify(locationService).getHeatmapData(siteId, start, end);
        }
    }

    @Nested
    class GetAlerts {

        @Test
        @DisplayName("delegates to locationService.getAlerts with organization id from principal")
        void delegatesToService() {
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(UUID.randomUUID())
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            GeofenceAlertDto alert = mock(GeofenceAlertDto.class);
            List<GeofenceAlertDto> expected = List.of(alert);
            when(locationService.getAlerts(principal.getOrganizationId())).thenReturn(expected);

            List<GeofenceAlertDto> result = locationController.getAlerts(principal);

            assertThat(result).isSameAs(expected);
            verify(locationService).getAlerts(principal.getOrganizationId());
        }
    }
}
