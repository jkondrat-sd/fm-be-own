package com.fm.be.modules.users;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.users.dto.CreateUserDto;
import com.fm.be.modules.users.dto.UpdateUserDto;
import com.fm.be.modules.users.dto.UserResponseDto;
import com.fm.be.modules.users.entity.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserService}.
 * Covers CRUD operations, duplicate checks, pagination, and role management.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(userId)
                .phone("+919876543210")
                .email("test@example.com")
                .passwordHash("$2a$10$encoded")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .preferredLang("en")
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("should create user with email and password")
        void createUser_validEmail_createsUser() {
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(UUID.randomUUID());
                u.setCreatedAt(Instant.now());
                u.setUpdatedAt(Instant.now());
                return u;
            });

            CreateUserDto dto = CreateUserDto.builder()
                    .email("new@example.com")
                    .password("password123")
                    .role(Role.ADMIN)
                    .organizationId(orgId)
                    .build();

            UserResponseDto result = userService.createUser(dto);

            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getRole()).isEqualTo(Role.ADMIN);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$encoded");
        }

        @Test
        @DisplayName("should create user with phone only (OTP user)")
        void createUser_phoneOnly_createsUser() {
            when(userRepository.existsByPhone("+919999999999")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(UUID.randomUUID());
                u.setCreatedAt(Instant.now());
                u.setUpdatedAt(Instant.now());
                return u;
            });

            CreateUserDto dto = CreateUserDto.builder()
                    .phone("+919999999999")
                    .role(Role.EMPLOYEE)
                    .organizationId(orgId)
                    .build();

            UserResponseDto result = userService.createUser(dto);

            assertThat(result.getPhone()).isEqualTo("+919999999999");
            assertThat(result.getRole()).isEqualTo(Role.EMPLOYEE);
        }

        @Test
        @DisplayName("should throw when duplicate email")
        void createUser_duplicateEmail_throwsException() {
            when(userRepository.existsByEmail("existing@example.com"))
                    .thenReturn(true);

            CreateUserDto dto = CreateUserDto.builder()
                    .email("existing@example.com")
                    .role(Role.EMPLOYEE)
                    .organizationId(orgId)
                    .build();

            assertThatThrownBy(() -> userService.createUser(dto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("should throw when duplicate phone")
        void createUser_duplicatePhone_throwsException() {
            when(userRepository.existsByPhone("+919876543210")).thenReturn(true);

            CreateUserDto dto = CreateUserDto.builder()
                    .phone("+919876543210")
                    .role(Role.EMPLOYEE)
                    .organizationId(orgId)
                    .build();

            assertThatThrownBy(() -> userService.createUser(dto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("phone");
        }

        @Test
        @DisplayName("should throw when neither email nor phone provided")
        void createUser_noLoginMethod_throwsException() {
            CreateUserDto dto = CreateUserDto.builder()
                    .role(Role.EMPLOYEE)
                    .organizationId(orgId)
                    .build();

            assertThatThrownBy(() -> userService.createUser(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("email or phone");
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("should update role successfully")
        void updateUser_changeRole_updatesRole() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            UpdateUserDto dto = UpdateUserDto.builder()
                    .role(Role.SUPERVISOR)
                    .build();

            UserResponseDto result = userService.updateUser(userId, dto);

            assertThat(result.getRole()).isEqualTo(Role.SUPERVISOR);
        }

        @Test
        @DisplayName("should update email and check for duplicates")
        void updateUser_changeEmail_checksUniqueness() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmail("new@example.com"))
                    .thenReturn(false);
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            UpdateUserDto dto = UpdateUserDto.builder()
                    .email("new@example.com")
                    .build();

            UserResponseDto result = userService.updateUser(userId, dto);

            assertThat(result.getEmail()).isEqualTo("new@example.com");
        }

        @Test
        @DisplayName("should throw when updating to duplicate email")
        void updateUser_duplicateEmail_throwsException() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmail("taken@example.com"))
                    .thenReturn(true);

            UpdateUserDto dto = UpdateUserDto.builder()
                    .email("taken@example.com")
                    .build();

            assertThatThrownBy(() -> userService.updateUser(userId, dto))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("should throw when user not found")
        void updateUser_notFound_throwsException() {
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            UpdateUserDto dto = UpdateUserDto.builder()
                    .role(Role.ADMIN)
                    .build();

            assertThatThrownBy(() -> userService.updateUser(randomId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should hash password when updating password")
        void updateUser_changePassword_hashesNewPassword() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("newpassword"))
                    .thenReturn("$2a$10$newencoded");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            UpdateUserDto dto = UpdateUserDto.builder()
                    .password("newpassword")
                    .build();

            userService.updateUser(userId, dto);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash())
                    .isEqualTo("$2a$10$newencoded");
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserByIdTests {

        @Test
        @DisplayName("should return user when found")
        void getUserById_found_returnsDto() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));

            UserResponseDto result = userService.getUserById(userId);

            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should throw when user not found")
        void getUserById_notFound_throwsException() {
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(randomId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("should return paginated users")
        void listUsers_returnsPagedResults() {
            Page<User> page = new PageImpl<>(List.of(testUser));
            when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

            PageRequestDto pageRequest = PageRequestDto.builder()
                    .page(1)
                    .limit(20)
                    .build();

            UserService.PaginatedUsers result = userService.listUsers(pageRequest);

            assertThat(result.users()).hasSize(1);
            assertThat(result.meta().getPage()).isEqualTo(1);
            assertThat(result.meta().getTotal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUserTests {

        @Test
        @DisplayName("should set isActive to false")
        void deactivateUser_setsInactive() {
            when(userRepository.findById(userId))
                    .thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(i -> i.getArgument(0));

            userService.deactivateUser(userId);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("should throw when user not found")
        void deactivateUser_notFound_throwsException() {
            UUID randomId = UUID.randomUUID();
            when(userRepository.findById(randomId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deactivateUser(randomId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
