package com.fm.be.modules.siteupdates;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.EmployeeRepository;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.siteupdates.dto.CreateSiteUpdateDto;
import com.fm.be.modules.siteupdates.dto.SiteUpdateResponseDto;
import com.fm.be.modules.siteupdates.entity.SiteUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for site update (field status report) operations.
 *
 * <p>Employees submit text + photo/video reports about site conditions.
 * Admins and supervisors can list and view updates per site.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteUpdateService {

    private final SiteUpdateRepository siteUpdateRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * Creates a new site update report.
     *
     * @param employeeId the reporting employee's UUID
     * @param dto the update data
     * @return the created update response
     */
    @Transactional
    public SiteUpdateResponseDto createSiteUpdate(UUID employeeId,
                                                   CreateSiteUpdateDto dto) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "id", employeeId));

        SiteUpdate update = SiteUpdate.builder()
                .employeeId(employeeId)
                .siteId(dto.getSiteId())
                .text(dto.getText())
                .mediaUrls(dto.getMediaUrls())
                .recordedAt(dto.getRecordedAt() != null
                        ? dto.getRecordedAt() : Instant.now())
                .build();
        update = siteUpdateRepository.save(update);

        log.info("Site update created: id={}, employee={}, site={}",
                update.getId(), employeeId, dto.getSiteId());

        return toResponseDto(update, employee);
    }

    /**
     * Lists site updates for a site with pagination.
     *
     * @param siteId the site UUID
     * @param pageRequest pagination params
     * @return paginated site updates
     */
    @Transactional(readOnly = true)
    public PaginatedSiteUpdates listBySite(UUID siteId,
                                            PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest);
        Page<SiteUpdate> page = siteUpdateRepository.findBySiteId(
                siteId, pageable);

        List<SiteUpdateResponseDto> updates = page.getContent().stream()
                .map(this::toResponseDtoWithEmployee)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedSiteUpdates(updates, meta);
    }

    /**
     * Gets a single site update by ID.
     *
     * @param updateId the update UUID
     * @return the update response
     */
    @Transactional(readOnly = true)
    public SiteUpdateResponseDto getById(UUID updateId) {
        SiteUpdate update = siteUpdateRepository.findById(updateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SiteUpdate", "id", updateId));
        return toResponseDtoWithEmployee(update);
    }

    /** Maps entity to response DTO, joining employee name. */
    private SiteUpdateResponseDto toResponseDtoWithEmployee(SiteUpdate update) {
        Employee employee = employeeRepository
                .findById(update.getEmployeeId()).orElse(null);
        return toResponseDto(update, employee);
    }

    /** Maps entity + employee to response DTO. */
    private SiteUpdateResponseDto toResponseDto(SiteUpdate update,
                                                 Employee employee) {
        return SiteUpdateResponseDto.builder()
                .id(update.getId())
                .employeeId(update.getEmployeeId())
                .employeeName(employee != null ? employee.getFullName() : null)
                .siteId(update.getSiteId())
                .text(update.getText())
                .mediaUrls(update.getMediaUrls())
                .recordedAt(update.getRecordedAt())
                .createdAt(update.getCreatedAt())
                .build();
    }

    /** Builds a Spring pageable from the shared PageRequestDto. */
    private PageRequest buildPageable(PageRequestDto pageRequest) {
        Sort sort = Sort.by(
                "ASC".equalsIgnoreCase(pageRequest.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                pageRequest.getSortBy() != null
                        ? pageRequest.getSortBy() : "recordedAt");
        return PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                sort);
    }

    /**
     * Record holding paginated site update results.
     */
    public record PaginatedSiteUpdates(List<SiteUpdateResponseDto> updates,
                                        PaginationMeta meta) {
    }
}
