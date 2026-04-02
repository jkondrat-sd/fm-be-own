package com.fm.be.modules.users;

import com.fm.be.common.annotation.CurrentUser;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.users.dto.CreateUserDto;
import com.fm.be.modules.users.dto.UpdateUserDto;
import com.fm.be.modules.users.dto.UserResponseDto;
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

import java.util.UUID;

/**
 * REST controller for admin user management endpoints.
 * Restricted to ADMIN and SUPER_ADMIN roles.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /v1/admin/users — List users (paginated)</li>
 *   <li>GET /v1/admin/users/{id} — Get user by ID</li>
 *   <li>POST /v1/admin/users — Create user</li>
 *   <li>PUT /v1/admin/users/{id} — Update user</li>
 *   <li>DELETE /v1/admin/users/{id} — Deactivate user</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Lists all users with pagination.
     *
     * @param pageRequest pagination parameters
     * @return paginated list of users
     */
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
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    /**
     * Creates a new user.
     *
     * @param dto the user creation data
     * @return the created user
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto createUser(@Valid @RequestBody CreateUserDto dto) {
        return userService.createUser(dto);
    }

    /**
     * Updates an existing user.
     *
     * @param id the user UUID
     * @param dto the update data
     * @return the updated user
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public UserResponseDto updateUser(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateUserDto dto) {
        return userService.updateUser(id, dto);
    }

    /**
     * Deactivates a user (soft delete).
     *
     * @param id the user UUID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public void deactivateUser(@PathVariable UUID id) {
        userService.deactivateUser(id);
    }
}
