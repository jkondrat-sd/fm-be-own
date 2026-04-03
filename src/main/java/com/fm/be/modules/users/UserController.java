package com.fm.be.modules.users;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.admin.AdminService;
import com.fm.be.modules.users.dto.CreateUserDto;
import com.fm.be.modules.users.dto.UpdateUserDto;
import com.fm.be.modules.users.dto.UserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for admin user management endpoints.
 * Restricted to ADMIN and SUPER_ADMIN roles.
 */
@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Admin user management — CRUD, role assignment, deactivation")
public class UserController {

    private final UserService userService;
    private final AdminService adminService;

    /**
     * Lists all users with pagination.
     *
     * @param pageRequest pagination parameters
     * @return paginated list of users
     */
    @Operation(summary = "List users", description = "Paginated list of all users. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Object> listUsers(@Valid PageRequestDto pageRequest) {
        UserService.PaginatedUsers result = userService.listUsers(pageRequest);
        return ApiResponse.paginated(result.users(), result.meta());
    }

    /**
     * Gets a single user by ID.
     *
     * @param id the user UUID
     * @return the user data
     */
    @Operation(summary = "Get user", description = "Retrieves user details by ID. Roles: ADMIN, SUPER_ADMIN")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    /**
     * Creates a new user.
     *
     * @param dto the user creation data
     * @param principal the authenticated admin user
     * @param request the HTTP request (for audit IP logging)
     * @return the created user
     */
    @Operation(summary = "Create user", description = "Creates a new user with role assignment. Audit logged. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto createUser(
            @Valid @RequestBody CreateUserDto dto,
            @CurrentUser UserPrincipal principal,
            HttpServletRequest request) {
        UserResponseDto created = userService.createUser(dto);
        adminService.logAuditEvent(
                principal.getUserId(), "CREATE_USER", "User",
                created.getId(),
                Map.of("role", dto.getRole().name()),
                request.getRemoteAddr());
        return created;
    }

    /**
     * Updates an existing user.
     *
     * @param id the user UUID
     * @param dto the update data
     * @param principal the authenticated admin user
     * @param request the HTTP request (for audit IP logging)
     * @return the updated user
     */
    @Operation(summary = "Update user", description = "Updates user fields (role, active status, etc.). Audit logged. Roles: ADMIN, SUPER_ADMIN")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserDto dto,
            @CurrentUser UserPrincipal principal,
            HttpServletRequest request) {
        UserResponseDto updated = userService.updateUser(id, dto);
        adminService.logAuditEvent(
                principal.getUserId(), "UPDATE_USER", "User",
                id, null, request.getRemoteAddr());
        return updated;
    }

    /**
     * Deactivates a user (soft delete).
     *
     * @param id the user UUID
     * @param principal the authenticated admin user
     * @param request the HTTP request (for audit IP logging)
     */
    @Operation(summary = "Deactivate user", description = "Soft-deletes a user by setting isActive=false. Audit logged. Roles: ADMIN, SUPER_ADMIN",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User deactivated"))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void deactivateUser(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal principal,
            HttpServletRequest request) {
        userService.deactivateUser(id);
        adminService.logAuditEvent(
                principal.getUserId(), "DEACTIVATE_USER", "User",
                id, null, request.getRemoteAddr());
    }
}
