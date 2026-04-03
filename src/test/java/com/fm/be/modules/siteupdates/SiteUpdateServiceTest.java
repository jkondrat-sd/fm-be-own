package com.fm.be.modules.siteupdates;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.siteupdates.dto.CreateSiteUpdateDto;
import com.fm.be.modules.siteupdates.dto.SiteUpdateResponseDto;
import com.fm.be.modules.siteupdates.entity.SiteUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
 * Unit tests for {@link SiteUpdateService}.
 * Covers create, list, and get operations.
 */
@ExtendWith(MockitoExtension.class)
class SiteUpdateServiceTest {

    @Mock
    private SiteUpdateRepository siteUpdateRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private SiteUpdateService siteUpdateService;

    private Employee testEmployee;
    private final UUID employeeId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testEmployee = Employee.builder()
                .id(employeeId)
                .fullName("John Doe")
                .siteId(siteId)
                .build();
    }

    @Nested
    @DisplayName("createSiteUpdate")
    class CreateSiteUpdateTests {

        @Test
        @DisplayName("should create update with text and media")
        void createSiteUpdate_withTextAndMedia_creates() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteUpdateRepository.save(any(SiteUpdate.class)))
                    .thenAnswer(i -> {
                        SiteUpdate u = i.getArgument(0);
                        u.setId(UUID.randomUUID());
                        u.setCreatedAt(Instant.now());
                        return u;
                    });

            CreateSiteUpdateDto dto = CreateSiteUpdateDto.builder()
                    .siteId(siteId)
                    .text("Site cleaning done")
                    .mediaUrls(List.of("https://cdn.example.com/img1.jpg"))
                    .build();

            SiteUpdateResponseDto result =
                    siteUpdateService.createSiteUpdate(employeeId, dto);

            assertThat(result.getText()).isEqualTo("Site cleaning done");
            assertThat(result.getMediaUrls()).hasSize(1);
            assertThat(result.getEmployeeName()).isEqualTo("John Doe");
        }

        @Test
        @DisplayName("should create update without media")
        void createSiteUpdate_textOnly_creates() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(siteUpdateRepository.save(any(SiteUpdate.class)))
                    .thenAnswer(i -> {
                        SiteUpdate u = i.getArgument(0);
                        u.setId(UUID.randomUUID());
                        u.setCreatedAt(Instant.now());
                        return u;
                    });

            CreateSiteUpdateDto dto = CreateSiteUpdateDto.builder()
                    .siteId(siteId)
                    .text("Guard on duty")
                    .build();

            SiteUpdateResponseDto result =
                    siteUpdateService.createSiteUpdate(employeeId, dto);

            assertThat(result.getText()).isEqualTo("Guard on duty");
        }

        @Test
        @DisplayName("should throw when employee not found")
        void createSiteUpdate_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(employeeRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            CreateSiteUpdateDto dto = CreateSiteUpdateDto.builder()
                    .siteId(siteId)
                    .text("Report")
                    .build();

            assertThatThrownBy(() ->
                    siteUpdateService.createSiteUpdate(randomId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listBySite")
    class ListBySiteTests {

        @Test
        @DisplayName("should return paginated updates for site")
        void listBySite_returnsPaginated() {
            SiteUpdate update = SiteUpdate.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .siteId(siteId)
                    .text("All clear")
                    .recordedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            Page<SiteUpdate> page = new PageImpl<>(List.of(update));
            when(siteUpdateRepository.findBySiteId(
                    eq(siteId), any(Pageable.class))).thenReturn(page);
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));

            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            SiteUpdateService.PaginatedSiteUpdates result =
                    siteUpdateService.listBySite(siteId, pageReq);

            assertThat(result.updates()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return update when found")
        void getById_found_returnsDto() {
            SiteUpdate update = SiteUpdate.builder()
                    .id(UUID.randomUUID())
                    .employeeId(employeeId)
                    .siteId(siteId)
                    .text("Reported issue")
                    .recordedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            when(siteUpdateRepository.findById(update.getId()))
                    .thenReturn(Optional.of(update));
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));

            SiteUpdateResponseDto result =
                    siteUpdateService.getById(update.getId());

            assertThat(result.getText()).isEqualTo("Reported issue");
        }

        @Test
        @DisplayName("should throw when not found")
        void getById_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(siteUpdateRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> siteUpdateService.getById(randomId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
