package com.fm.be.modules.employees;

import com.fm.be.common.constants.DocumentType;
import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.employees.dto.CreateEmployeeDto;
import com.fm.be.modules.employees.dto.EmployeeDocumentDto;
import com.fm.be.modules.employees.dto.EmployeeResponseDto;
import com.fm.be.modules.employees.dto.UpdateEmployeeDto;
import com.fm.be.modules.employees.entity.Employee;
import com.fm.be.modules.employees.entity.EmployeeDocument;
import com.fm.be.modules.users.UserRepository;
import com.fm.be.modules.users.entity.User;
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
 * Service for employee CRUD, document management, and face registration.
 *
 * <p>Employee creation also creates the associated user account.
 * PII fields (Aadhaar, PAN) are stored encrypted. Face embeddings
 * are stored as float arrays for matching during attendance punch.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeDocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new employee along with their user account.
     *
     * @param dto the employee creation data
     * @return the created employee response
     * @throws DuplicateResourceException if user already exists for that phone/email
     */
    @Transactional
    public EmployeeResponseDto createEmployee(CreateEmployeeDto dto) {
        // Validate no duplicate user
        if (dto.getPhone() != null && userRepository.existsByPhone(dto.getPhone())) {
            throw new DuplicateResourceException("User", "phone", dto.getPhone());
        }
        if (dto.getEmail() != null && userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("User", "email", dto.getEmail());
        }

        // Create user account first
        User user = User.builder()
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .role(dto.getRole() != null ? dto.getRole() : Role.EMPLOYEE)
                .organizationId(dto.getOrganizationId())
                .build();
        user = userRepository.save(user);

        // Create employee record linked to user
        Employee employee = Employee.builder()
                .userId(user.getId())
                .fullName(dto.getFullName())
                .dob(dto.getDob())
                .siteId(dto.getSiteId())
                .organizationId(dto.getOrganizationId())
                .build();
        employee = employeeRepository.save(employee);

        log.info("Employee created: id={}, userId={}, name={}",
                employee.getId(), user.getId(), employee.getFullName());

        return toResponseDto(employee, user);
    }

    /**
     * Updates an existing employee.
     *
     * @param id the employee UUID
     * @param dto the update data
     * @return the updated employee response
     */
    @Transactional
    public EmployeeResponseDto updateEmployee(UUID id, UpdateEmployeeDto dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "id", id));

        if (dto.getFullName() != null) {
            employee.setFullName(dto.getFullName());
        }
        if (dto.getDob() != null) {
            employee.setDob(dto.getDob());
        }
        if (dto.getSiteId() != null) {
            employee.setSiteId(dto.getSiteId());
        }

        Employee savedEmployee = employeeRepository.save(employee);
        User user = userRepository.findById(savedEmployee.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", savedEmployee.getUserId()));

        log.info("Employee updated: id={}", savedEmployee.getId());
        return toResponseDto(savedEmployee, user);
    }

    /**
     * Gets an employee by their ID.
     *
     * @param id the employee UUID
     * @return the employee response
     */
    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployeeById(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "id", id));
        User user = userRepository.findById(employee.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", employee.getUserId()));
        return toResponseDto(employee, user);
    }

    /**
     * Gets an employee's profile by their user ID (for self-lookup).
     *
     * @param userId the user's UUID
     * @return the employee response
     */
    @Transactional(readOnly = true)
    public EmployeeResponseDto getProfileByUserId(UUID userId) {
        Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "userId", userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", userId));
        return toResponseDto(employee, user);
    }

    /**
     * Lists employees in an organization with pagination.
     *
     * @param organizationId the organization UUID
     * @param pageRequest pagination parameters
     * @return paginated employee list
     */
    @Transactional(readOnly = true)
    public PaginatedEmployees listEmployees(UUID organizationId,
                                            PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest);

        Page<Employee> page = employeeRepository.findByOrganizationId(
                organizationId, pageable);

        List<EmployeeResponseDto> employees = page.getContent().stream()
                .map(emp -> {
                    User user = userRepository.findById(emp.getUserId())
                            .orElse(null);
                    return toResponseDto(emp, user);
                })
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedEmployees(employees, meta);
    }

    /**
     * Searches employees by name within an organization.
     *
     * @param query the search query
     * @param organizationId the organization UUID
     * @param pageRequest pagination parameters
     * @return matching employees
     */
    @Transactional(readOnly = true)
    public PaginatedEmployees searchEmployees(String query, UUID organizationId,
                                              PageRequestDto pageRequest) {
        PageRequest pageable = buildPageable(pageRequest);
        String pattern = "%" + query.toLowerCase() + "%";

        Page<Employee> page = employeeRepository.searchByName(
                pattern, organizationId, pageable);

        List<EmployeeResponseDto> employees = page.getContent().stream()
                .map(emp -> {
                    User user = userRepository.findById(emp.getUserId())
                            .orElse(null);
                    return toResponseDto(emp, user);
                })
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedEmployees(employees, meta);
    }

    /**
     * Registers a face embedding for an employee (for facial verification).
     *
     * @param employeeId the employee UUID
     * @param embedding the face embedding float array
     */
    @Transactional
    public void registerFace(UUID employeeId, float[] embedding) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee", "id", employeeId));
        employee.setFaceEmbedding(embedding);
        employeeRepository.save(employee);
        log.info("Face registered for employee: id={}", employeeId);
    }

    /**
     * Adds a verified/unverified document to an employee's records.
     *
     * @param employeeId the employee UUID
     * @param type the document type
     * @param fileUrl the URL of the uploaded document
     * @return the document DTO
     */
    @Transactional
    public EmployeeDocumentDto addDocument(UUID employeeId, DocumentType type,
                                           String fileUrl) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }

        EmployeeDocument document = EmployeeDocument.builder()
                .employeeId(employeeId)
                .type(type)
                .fileUrl(fileUrl)
                .build();
        document = documentRepository.save(document);
        log.info("Document added for employee {}: type={}", employeeId, type);
        return toDocumentDto(document);
    }

    /**
     * Verifies a document (admin action).
     *
     * @param documentId the document UUID
     * @param verifierId the admin user UUID who verified it
     * @return the updated document DTO
     */
    @Transactional
    public EmployeeDocumentDto verifyDocument(UUID documentId, UUID verifierId) {
        EmployeeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeeDocument", "id", documentId));
        document.setVerified(true);
        document.setVerifiedBy(verifierId);
        document.setVerifiedAt(Instant.now());
        document = documentRepository.save(document);
        log.info("Document verified: id={}, by={}", documentId, verifierId);
        return toDocumentDto(document);
    }

    /**
     * Builds a pageable from the PageRequestDto.
     */
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
     * Maps Employee + User to EmployeeResponseDto.
     */
    private EmployeeResponseDto toResponseDto(Employee employee, User user) {
        List<EmployeeDocument> docs =
                documentRepository.findByEmployeeId(employee.getId());

        return EmployeeResponseDto.builder()
                .id(employee.getId())
                .userId(employee.getUserId())
                .fullName(employee.getFullName())
                .phone(user != null ? user.getPhone() : null)
                .email(user != null ? user.getEmail() : null)
                .role(user != null ? user.getRole() : null)
                .dob(employee.getDob())
                .siteId(employee.getSiteId())
                .organizationId(employee.getOrganizationId())
                .hasFaceRegistered(employee.getFaceEmbedding() != null
                        && employee.getFaceEmbedding().length > 0)
                .documents(docs.stream()
                        .map(this::toDocumentDto)
                        .collect(Collectors.toList()))
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .build();
    }

    /**
     * Maps EmployeeDocument entity to DTO.
     */
    private EmployeeDocumentDto toDocumentDto(EmployeeDocument doc) {
        return EmployeeDocumentDto.builder()
                .id(doc.getId())
                .type(doc.getType())
                .fileUrl(doc.getFileUrl())
                .isVerified(doc.isVerified())
                .verifiedBy(doc.getVerifiedBy())
                .verifiedAt(doc.getVerifiedAt())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    /**
     * Record holding paginated employee results.
     */
    public record PaginatedEmployees(List<EmployeeResponseDto> employees,
                                     PaginationMeta meta) {
    }
}
