package com.fm.be.common.interceptor;

import com.fm.be.common.constants.Role;
import com.fm.be.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantInterceptor}.
 */
class TenantInterceptorTest {

    private TenantInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("should set organization ID from authenticated user")
    void preHandle_authenticatedUser_setsOrgId() {
        UUID orgId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.ADMIN)
                .organizationId(orgId)
                .siteIds(List.of())
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(TenantContext.getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("should not set context when unauthenticated")
    void preHandle_unauthenticated_noContext() {
        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(TenantContext.getOrganizationId()).isNull();
    }

    @Test
    @DisplayName("should not set context when principal has null org ID")
    void preHandle_nullOrgId_noContext() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .role(Role.EMPLOYEE)
                .organizationId(null)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        interceptor.preHandle(request, response, null);

        assertThat(TenantContext.getOrganizationId()).isNull();
    }

    @Test
    @DisplayName("should clear context on afterCompletion")
    void afterCompletion_clearsContext() {
        UUID orgId = UUID.randomUUID();
        TenantContext.setOrganizationId(orgId);
        assertThat(TenantContext.getOrganizationId()).isEqualTo(orgId);

        interceptor.afterCompletion(request, response, null, null);

        assertThat(TenantContext.getOrganizationId()).isNull();
    }

    @Test
    @DisplayName("should handle non-UserPrincipal authentication gracefully")
    void preHandle_nonUserPrincipal_noContext() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "string-principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(TenantContext.getOrganizationId()).isNull();
    }
}
