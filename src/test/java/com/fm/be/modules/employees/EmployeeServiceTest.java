package com.fm.be.modules.employees;

import com.fm.be.common.constants.DocumentType;
import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.PageRequestDto;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmployeeService}.
 * Covers CRUD, document management, face registration, pagination, and search.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeDocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee testEmployee;
    private User testUser;
    private final UUID employeeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(userId)
                .phone("+919876543210")
                .email("employee@example.com")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        testEmployee = Employee.builder()
                .id(employeeId)
                .userId(userId)
                .fullName("Rahul Kumar")
                .dob(LocalDate.of(1995, 5, 15))
                .siteId(siteId)
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createEmployee")
    class CreateEmployeeTests {

        @Test
        @DisplayName("should create employee and user together")
        void createEmployee_valid_createsUserAndEmployee() {
            when(userRepository.existsByPhone("+919999999999")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(UUID.randomUUID());
                u.setCreatedAt(Instant.now());
                u.setUpdatedAt(Instant.now());
                return u;
            });
            when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> {
                Employee e = i.getArgument(0);
                e.setId(UUID.randomUUID());
                e.setCreatedAt(Instant.now());
                e.setUpdatedAt(Instant.now());
                return e;
            });
            when(documentRepository.findByEmployeeId(any())).thenReturn(List.of());

            CreateEmployeeDto dto = CreateEmployeeDto.builder()
                    .fullName("New Employee")
                    .phone("+919999999999")
                    .organizationId(orgId)
                    .siteId(siteId)
                    .build();

            EmployeeResponseDto result = employeeService.createEmployee(dto);

            assertThat(result.getFullName()).isEqualTo("New Employee");
            assertThat(result.getPhone()).isEqualTo("+919999999999");
            verify(userRepository).save(any(User.class));
            verify(employeeRepository).save(any(Employee.class));
        }

        @Test
        @DisplayName("should throw when duplicate phone")
        void createEmployee_duplicatePhone_throws() {
            when(userRepository.existsByPhone("+919876543210")).thenReturn(true);

            CreateEmployeeDto dto = CreateEmployeeDto.builder()
                    .fullName("Duplicate")
                    .phone("+919876543210")
                    .organizationId(orgId)
                    .build();

            assertThatThrownBy(() -> employeeService.createEmployee(dto))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should throw when duplicate email")
        void createEmployee_duplicateEmail_throws() {
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            CreateEmployeeDto dto = CreateEmployeeDto.builder()
                    .fullName("Duplicate")
                    .email("taken@example.com")
                    .organizationId(orgId)
                    .build();

            assertThatThrownBy(() -> employeeService.createEmployee(dto))
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Nested
    @DisplayName("updateEmployee")
    class UpdateEmployeeTests {

        @Test
        @DisplayName("should update employee name and site")
        void updateEmployee_validFields_updates() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class)))
                    .thenAnswer(i -> i.getArgument(0));
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(documentRepository.findByEmployeeId(employeeId))
                    .thenReturn(List.of());

            UUID newSiteId = UUID.randomUUID();
            UpdateEmployeeDto dto = UpdateEmployeeDto.builder()
                    .fullName("Updated Name")
                    .siteId(newSiteId)
                    .build();

            EmployeeResponseDto result = employeeService.updateEmployee(
                    employeeId, dto);

            assertThat(result.getFullName()).isEqualTo("Updated Name");
            assertThat(result.getSiteId()).isEqualTo(newSiteId);
        }

        @Test
        @DisplayName("should throw when employee not found")
        void updateEmployee_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(employeeRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.updateEmployee(
                    randomId, new UpdateEmployeeDto()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getEmployeeById")
    class GetEmployeeByIdTests {

        @Test
        @DisplayName("should return employee when found")
        void getEmployeeById_found_returnsDto() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(documentRepository.findByEmployeeId(employeeId))
                    .thenReturn(List.of());

            EmployeeResponseDto result = employeeService.getEmployeeById(
                    employeeId);

            assertThat(result.getFullName()).isEqualTo("Rahul Kumar");
            assertThat(result.isHasFaceRegistered()).isFalse();
        }
    }

    @Nested
    @DisplayName("listEmployees")
    class ListEmployeesTests {

        @Test
        @DisplayName("should return paginated employees")
        void listEmployees_returnsPaginated() {
            Page<Employee> page = new PageImpl<>(List.of(testEmployee));
            when(employeeRepository.findByOrganizationId(
                    eq(orgId), any(Pageable.class))).thenReturn(page);
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(documentRepository.findByEmployeeId(employeeId))
                    .thenReturn(List.of());

            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            EmployeeService.PaginatedEmployees result =
                    employeeService.listEmployees(orgId, pageReq);

            assertThat(result.employees()).hasSize(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("searchEmployees")
    class SearchEmployeesTests {

        @Test
        @DisplayName("should search employees by name")
        void searchEmployees_returnsMatches() {
            Page<Employee> page = new PageImpl<>(List.of(testEmployee));
            when(employeeRepository.searchByName(
                    eq("%rahul%"), eq(orgId), any(Pageable.class)))
                    .thenReturn(page);
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(documentRepository.findByEmployeeId(employeeId))
                    .thenReturn(List.of());

            PageRequestDto pageReq = PageRequestDto.builder()
                    .page(1).limit(20).build();

            EmployeeService.PaginatedEmployees result =
                    employeeService.searchEmployees("rahul", orgId, pageReq);

            assertThat(result.employees()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("registerFace")
    class RegisterFaceTests {

        @Test
        @DisplayName("should store face embedding")
        void registerFace_valid_storesEmbedding() {
            when(employeeRepository.findById(employeeId))
                    .thenReturn(Optional.of(testEmployee));
            when(employeeRepository.save(any(Employee.class)))
                    .thenAnswer(i -> i.getArgument(0));

            float[] embedding = {0.1f, 0.2f, 0.3f};
            employeeService.registerFace(employeeId, embedding);

            verify(employeeRepository).save(any(Employee.class));
            assertThat(testEmployee.getFaceEmbedding()).isEqualTo(embedding);
        }

        @Test
        @DisplayName("should throw when employee not found")
        void registerFace_notFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(employeeRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.registerFace(
                    randomId, new float[]{0.1f}))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("addDocument")
    class AddDocumentTests {

        @Test
        @DisplayName("should add document successfully")
        void addDocument_valid_addsDocument() {
            when(employeeRepository.existsById(employeeId)).thenReturn(true);
            when(documentRepository.save(any(EmployeeDocument.class)))
                    .thenAnswer(i -> {
                        EmployeeDocument d = i.getArgument(0);
                        d.setId(UUID.randomUUID());
                        d.setCreatedAt(Instant.now());
                        d.setUpdatedAt(Instant.now());
                        return d;
                    });

            EmployeeDocumentDto result = employeeService.addDocument(
                    employeeId, DocumentType.AADHAAR,
                    "https://storage.example.com/aadhaar.pdf");

            assertThat(result.getType()).isEqualTo(DocumentType.AADHAAR);
            assertThat(result.getFileUrl())
                    .isEqualTo("https://storage.example.com/aadhaar.pdf");
            assertThat(result.isVerified()).isFalse();
        }

        @Test
        @DisplayName("should throw when employee not found")
        void addDocument_employeeNotFound_throws() {
            UUID randomId = UUID.randomUUID();
            when(employeeRepository.existsById(randomId)).thenReturn(false);

            assertThatThrownBy(() -> employeeService.addDocument(
                    randomId, DocumentType.PAN, "url"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("verifyDocument")
    class VerifyDocumentTests {

        @Test
        @DisplayName("should verify document successfully")
        void verifyDocument_valid_verifies() {
            UUID docId = UUID.randomUUID();
            UUID verifierId = UUID.randomUUID();

            EmployeeDocument doc = EmployeeDocument.builder()
                    .id(docId)
                    .employeeId(employeeId)
                    .type(DocumentType.AADHAAR)
                    .fileUrl("https://storage.example.com/aadhaar.pdf")
                    .isVerified(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(documentRepository.findById(docId))
                    .thenReturn(Optional.of(doc));
            when(documentRepository.save(any(EmployeeDocument.class)))
                    .thenAnswer(i -> i.getArgument(0));

            EmployeeDocumentDto result = employeeService.verifyDocument(
                    docId, verifierId);

            assertThat(result.isVerified()).isTrue();
            assertThat(result.getVerifiedBy()).isEqualTo(verifierId);
            assertThat(result.getVerifiedAt()).isNotNull();
        }
    }
}
