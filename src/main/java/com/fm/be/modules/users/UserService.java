package com.fm.be.modules.users;

import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.exception.DuplicateResourceException;
import com.fm.be.common.exception.ResourceNotFoundException;
import com.fm.be.modules.users.dto.CreateUserDto;
import com.fm.be.modules.users.dto.UpdateUserDto;
import com.fm.be.modules.users.dto.UserResponseDto;
import com.fm.be.modules.users.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for user CRUD operations and role management.
 *
 * <p>Only ADMIN and SUPER_ADMIN roles can access these operations
 * (enforced at the controller level via @PreAuthorize).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new user. Validates that email and phone are unique.
     *
     * @param dto the user creation data
     * @return the created user response
     * @throws DuplicateResourceException if email or phone already exists
     * @throws IllegalArgumentException if neither email nor phone provided
     */
    @Transactional
    public UserResponseDto createUser(CreateUserDto dto) {
        validateLoginMethodProvided(dto.getEmail(), dto.getPhone());
        checkForDuplicates(dto.getEmail(), dto.getPhone());

        User user = User.builder()
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .passwordHash(dto.getPassword() != null
                        ? passwordEncoder.encode(dto.getPassword()) : null)
                .role(dto.getRole())
                .preferredLang(dto.getPreferredLang())
                .organizationId(dto.getOrganizationId())
                .build();

        User saved = userRepository.save(user);
        log.info("User created: id={}, role={}", saved.getId(), saved.getRole());
        return toResponseDto(saved);
    }

    /**
     * Updates an existing user. Only non-null fields are updated.
     *
     * @param id the user UUID
     * @param dto the update data
     * @return the updated user response
     * @throws ResourceNotFoundException if user not found
     * @throws DuplicateResourceException if new email/phone conflicts
     */
    @Transactional
    public UserResponseDto updateUser(UUID id, UpdateUserDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", id));

        if (dto.getEmail() != null && !dto.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new DuplicateResourceException(
                        "User", "email", dto.getEmail());
            }
            user.setEmail(dto.getEmail());
        }

        if (dto.getPhone() != null && !dto.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(dto.getPhone())) {
                throw new DuplicateResourceException(
                        "User", "phone", dto.getPhone());
            }
            user.setPhone(dto.getPhone());
        }

        if (dto.getPassword() != null) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }

        if (dto.getIsActive() != null) {
            user.setActive(dto.getIsActive());
        }

        if (dto.getPreferredLang() != null) {
            user.setPreferredLang(dto.getPreferredLang());
        }

        User saved = userRepository.save(user);
        log.info("User updated: id={}", saved.getId());
        return toResponseDto(saved);
    }

    /**
     * Retrieves a single user by ID.
     *
     * @param id the user UUID
     * @return the user response
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", id));
        return toResponseDto(user);
    }

    /**
     * Lists users with pagination and sorting.
     *
     * @param pageRequest the pagination parameters
     * @return paginated result with user list and metadata
     */
    @Transactional(readOnly = true)
    public PaginatedUsers listUsers(PageRequestDto pageRequest) {
        Sort sort = Sort.by(
                "ASC".equalsIgnoreCase(pageRequest.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                pageRequest.getSortBy() != null
                        ? pageRequest.getSortBy() : "createdAt");

        PageRequest pageable = PageRequest.of(
                pageRequest.getPage() - 1,
                pageRequest.getLimit(),
                sort);

        Page<User> page = userRepository.findAll(pageable);

        List<UserResponseDto> users = page.getContent().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());

        PaginationMeta meta = PaginationMeta.builder()
                .page(pageRequest.getPage())
                .limit(pageRequest.getLimit())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();

        return new PaginatedUsers(users, meta);
    }

    /**
     * Deactivates a user by setting isActive = false.
     *
     * @param id the user UUID
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", id));
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: id={}", id);
    }

    /**
     * Validates that at least one login method (email or phone) is provided.
     */
    private void validateLoginMethodProvided(String email, String phone) {
        if ((email == null || email.isBlank())
                && (phone == null || phone.isBlank())) {
            throw new IllegalArgumentException(
                    "At least one of email or phone must be provided");
        }
    }

    /**
     * Checks for duplicate email and phone conflicts.
     */
    private void checkForDuplicates(String email, String phone) {
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("User", "phone", phone);
        }
    }

    /**
     * Maps a User entity to a UserResponseDto.
     */
    private UserResponseDto toResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.isActive())
                .preferredLang(user.getPreferredLang())
                .organizationId(user.getOrganizationId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Record holding paginated user results.
     */
    public record PaginatedUsers(List<UserResponseDto> users,
                                 PaginationMeta meta) {
    }
}
