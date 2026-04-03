package com.fm.be.modules.users;

import com.fm.be.common.constants.Role;
import com.fm.be.common.dto.ApiResponse;
import com.fm.be.common.dto.PageRequestDto;
import com.fm.be.common.dto.PaginationMeta;
import com.fm.be.common.security.UserPrincipal;
import com.fm.be.modules.admin.AdminService;
import com.fm.be.modules.users.dto.CreateUserDto;
import com.fm.be.modules.users.dto.UpdateUserDto;
import com.fm.be.modules.users.dto.UserResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private AdminService adminService;

    @InjectMocks
    private UserController userController;

    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        @DisplayName("delegates to userService and returns paginated ApiResponse")
        void listUsers_wrapsPaginatedResult() {
            PageRequestDto pageRequest = mock(PageRequestDto.class);
            UserResponseDto user = mock(UserResponseDto.class);
            PaginationMeta meta = PaginationMeta.builder()
                    .page(1)
                    .limit(10)
                    .total(0)
                    .totalPages(0)
                    .build();
            UserService.PaginatedUsers paginated =
                    new UserService.PaginatedUsers(List.of(user), meta);
            when(userService.listUsers(pageRequest)).thenReturn(paginated);

            ApiResponse<Object> response = userController.listUsers(pageRequest);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(paginated.users());
            assertThat(response.getMeta()).isEqualTo(paginated.meta());
            verify(userService).listUsers(pageRequest);
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("delegates to userService")
        void getUserById_delegatesToService() {
            UUID id = UUID.randomUUID();
            UserResponseDto expected = mock(UserResponseDto.class);
            when(userService.getUserById(id)).thenReturn(expected);

            UserResponseDto result = userController.getUserById(id);

            assertThat(result).isSameAs(expected);
            verify(userService).getUserById(id);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates user then logs audit event with role metadata")
        void createUser_callsUserServiceAndAudit() {
            CreateUserDto dto = mock(CreateUserDto.class);
            when(dto.getRole()).thenReturn(Role.ADMIN);
            UUID actorId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(actorId)
                    .role(Role.SUPER_ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            UserResponseDto created = mock(UserResponseDto.class);
            UUID createdId = UUID.randomUUID();
            when(created.getId()).thenReturn(createdId);
            when(userService.createUser(dto)).thenReturn(created);

            UserResponseDto result = userController.createUser(dto, principal, request);

            assertThat(result).isSameAs(created);
            verify(userService).createUser(dto);
            verify(adminService).logAuditEvent(
                    eq(actorId),
                    eq("CREATE_USER"),
                    eq("User"),
                    eq(createdId),
                    eq(Map.of("role", Role.ADMIN.name())),
                    eq("127.0.0.1"));
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updates user then logs audit event")
        void updateUser_callsUserServiceAndAudit() {
            UUID id = UUID.randomUUID();
            UpdateUserDto dto = mock(UpdateUserDto.class);
            UUID actorId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(actorId)
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            UserResponseDto updated = mock(UserResponseDto.class);
            when(userService.updateUser(id, dto)).thenReturn(updated);

            UserResponseDto result = userController.updateUser(id, dto, principal, request);

            assertThat(result).isSameAs(updated);
            verify(userService).updateUser(id, dto);
            verify(adminService).logAuditEvent(
                    eq(actorId),
                    eq("UPDATE_USER"),
                    eq("User"),
                    eq(id),
                    isNull(),
                    eq("127.0.0.1"));
        }
    }

    @Nested
    @DisplayName("deactivateUser")
    class DeactivateUser {

        @Test
        @DisplayName("deactivates user then logs audit event")
        void deactivateUser_callsUserServiceAndAudit() {
            UUID id = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(actorId)
                    .role(Role.ADMIN)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            userController.deactivateUser(id, principal, request);

            verify(userService).deactivateUser(id);
            verify(adminService).logAuditEvent(
                    eq(actorId),
                    eq("DEACTIVATE_USER"),
                    eq("User"),
                    eq(id),
                    isNull(),
                    eq("127.0.0.1"));
        }
    }
}
