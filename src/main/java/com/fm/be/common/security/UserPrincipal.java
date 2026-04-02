package com.fm.be.common.security;

import com.fm.be.common.constants.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security principal carrying the authenticated user's identity.
 * Extracted from the JWT on each request by {@link JwtAuthenticationFilter}.
 *
 * <p>Contains: userId, role, organizationId, and siteIds from the JWT payload.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private UUID userId;
    private Role role;
    private UUID organizationId;
    private List<UUID> siteIds;

    /**
     * Maps the user's role to a Spring Security granted authority.
     * Prefixed with "ROLE_" for @PreAuthorize / @Secured compatibility.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null; // Not used — JWT-based auth
    }

    @Override
    public String getUsername() {
        return userId != null ? userId.toString() : null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
