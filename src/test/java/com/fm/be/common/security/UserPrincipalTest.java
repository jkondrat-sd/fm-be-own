package com.fm.be.common.security;

import com.fm.be.common.constants.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UserPrincipal}.
 */
class UserPrincipalTest {

    @Test
    void getAuthorities_mapsRoleToRolePrefixedAuthority() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(UUID.randomUUID())
                .siteIds(List.of())
                .build();

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();

        assertEquals(1, authorities.size());
        assertEquals("ROLE_ADMIN", authorities.iterator().next().getAuthority());
    }

    @Test
    void getAuthorities_allRoles() {
        for (Role role : Role.values()) {
            UserPrincipal principal = UserPrincipal.builder()
                    .userId(UUID.randomUUID())
                    .role(role)
                    .organizationId(UUID.randomUUID())
                    .siteIds(List.of())
                    .build();

            String expected = "ROLE_" + role.name();
            assertEquals(expected, principal.getAuthorities().iterator().next().getAuthority());
        }
    }

    @Test
    void getUsername_returnsUserIdString() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .role(Role.EMPLOYEE)
                .build();

        assertEquals(userId.toString(), principal.getUsername());
    }

    @Test
    void accountStatus_allTrue() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.EMPLOYEE)
                .build();

        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isCredentialsNonExpired());
        assertTrue(principal.isEnabled());
    }

    @Test
    void getPassword_returnsNull() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.EMPLOYEE)
                .build();

        assertNull(principal.getPassword());
    }
}
