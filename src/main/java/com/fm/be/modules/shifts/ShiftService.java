package com.fm.be.modules.shifts;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.shifts.dto.CreateShiftDto;
import com.fm.be.modules.shifts.dto.ScheduleResponseDto;
import com.fm.be.modules.shifts.dto.ShiftAssignmentDto;
import com.fm.be.modules.shifts.dto.UpdateShiftDto;
import com.fm.be.modules.shifts.entity.Shift;
import com.fm.be.modules.shifts.entity.ShiftAssignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for shift CRUD and employee assignment operations.
 *
 * <p>Shift assignments serve as the mobile employee's "task view" —
 * there is no separate task entity. Today's shift is cached in Redis
 * for fast mobile access.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private static final String SHIFT_CACHE_PREFIX = "shift:";

    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * Creates a new shift template for a site.
     *
     * @param dto the shift creation data
     * @return the created shift
     */
    @Transactional
    public Shift createShift(CreateShiftDto dto) {
        Shift shift = Shift.builder()
                .siteId(dto.getSiteId())
                .name(dto.getName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .build();
        shift = shiftRepository.save(shift);
        log.info("Shift created: id={}, name={}, site={}",
                shift.getId(), shift.getName(), shift.getSiteId());
        return shift;
    }

    /**
     * Updates an existing shift template.
     *
     * @param shiftId the shift UUID
     * @param dto the update data
     * @return the updated shift
     */
    @Transactional
    public Shift updateShift(UUID shiftId, UpdateShiftDto dto) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift", "id", shiftId));

        if (dto.getName() != null) {
            shift.setName(dto.getName());
        }
        if (dto.getStartTime() != null) {
            shift.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            shift.setEndTime(dto.getEndTime());
        }
        if (dto.getIsActive() != null) {
            shift.setActive(dto.getIsActive());
        }

        shift = shiftRepository.save(shift);
        log.info("Shift updated: id={}", shiftId);
        return shift;
    }

    /**
     * Gets a shift by ID.
     */
    @Transactional(readOnly = true)
    public Shift getShiftById(UUID shiftId) {
        return shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift", "id", shiftId));
    }

    /**
     * Lists shifts for a site with pagination.
     */
    @Transactional(readOnly = true)
    public PaginatedShifts listShifts(UUID siteId, PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest);
        Page<Shift> page = shiftRepository.findBySiteId(siteId, pageable);

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedShifts(page.getContent(), meta);
    }

    /**
     * Assigns an employee to a shift on a specific date.
     * Prevents double-booking via the UNIQUE(employeeId, date) constraint.
     *
     * @param dto the assignment data
     * @return the created assignment
     * @throws DuplicateResourceException if employee already assigned that day
     */
    @Transactional
    public ShiftAssignment assignEmployee(ShiftAssignmentDto dto) {
        // Verify shift exists
        if (!shiftRepository.existsById(dto.getShiftId())) {
            throw new ResourceNotFoundException(
                    "Shift", "id", dto.getShiftId());
        }

        // Check for duplicate assignment on same date
        if (assignmentRepository.existsByEmployeeIdAndDate(
                dto.getEmployeeId(), dto.getDate())) {
            throw new DuplicateResourceException(
                    "ShiftAssignment", "employeeId+date",
                    dto.getEmployeeId() + "+" + dto.getDate());
        }

        ShiftAssignment assignment = ShiftAssignment.builder()
                .employeeId(dto.getEmployeeId())
                .shiftId(dto.getShiftId())
                .date(dto.getDate())
                .build();
        assignment = assignmentRepository.save(assignment);

        // Invalidate cached shift for this employee+date
        invalidateShiftCache(dto.getEmployeeId(), dto.getDate());

        log.info("Shift assigned: employee={}, shift={}, date={}",
                dto.getEmployeeId(), dto.getShiftId(), dto.getDate());
        return assignment;
    }

    /**
     * Gets the schedule for an employee within a date range.
     * Provides data for the mobile "task view".
     *
     * @param employeeId the employee UUID
     * @param startDate range start
     * @param endDate range end
     * @return list of schedule entries
     */
    @Transactional(readOnly = true)
    public List<ScheduleResponseDto> getSchedule(UUID employeeId,
                                                  LocalDate startDate,
                                                  LocalDate endDate) {
        List<ShiftAssignment> assignments = assignmentRepository
                .findByEmployeeIdAndDateBetween(employeeId, startDate, endDate);

        return assignments.stream()
                .map(this::toScheduleResponse)
                .collect(Collectors.toList());
    }

    /**
     * Invalidates the cached shift for a given employee and date.
     */
    private void invalidateShiftCache(UUID employeeId, LocalDate date) {
        String cacheKey = String.format("%s%s:%s",
                SHIFT_CACHE_PREFIX, employeeId, date);
        redisTemplate.delete(cacheKey);
        log.debug("Shift cache invalidated: {}", cacheKey);
    }

    /** Maps ShiftAssignment to ScheduleResponseDto by joining Shift data. */
    private ScheduleResponseDto toScheduleResponse(ShiftAssignment assignment) {
        Shift shift = shiftRepository.findById(assignment.getShiftId())
                .orElse(null);
        return ScheduleResponseDto.builder()
                .assignmentId(assignment.getId())
                .shiftId(assignment.getShiftId())
                .shiftName(shift != null ? shift.getName() : null)
                .siteId(shift != null ? shift.getSiteId() : null)
                .startTime(shift != null ? shift.getStartTime() : null)
                .endTime(shift != null ? shift.getEndTime() : null)
                .date(assignment.getDate())
                .build();
    }

    /** Builds a Spring pageable from the shared PageRequestDto. */
    private PageRequest buildPageable(PageRequestDto pageRequest) {
        Sort sort = Sort.by(
                "ASC".equalsIgnoreCase(pageRequest.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                pageRequest.getSortBy() != null
                        ? pageRequest.getSortBy() : "createdAt");
        return PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                sort);
    }

    /**
     * Record holding paginated shift results.
     */
    public record PaginatedShifts(List<Shift> shifts, PaginationMeta meta) {
    }
}
