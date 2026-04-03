package com.fm.be.common.interceptor;

import com.fm.be.common.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Multi-tenancy interceptor that extracts the organization ID from
 * the authenticated user's JWT and stores it in {@link TenantContext}.
 *
 * <p>Every downstream service/repository query should reference
 * {@code TenantContext.getOrganizationId()} to enforce data isolation.
 * PostgreSQL Row-Level Security (RLS) serves as an additional safety net.</p>
 */
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    /**
     * Extracts organization ID from the authenticated user and sets it
     * in the thread-local TenantContext.
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof UserPrincipal principal) {
            if (principal.getOrganizationId() != null) {
                TenantContext.setOrganizationId(
                        principal.getOrganizationId());
                log.debug("Tenant context set: orgId={}",
                        principal.getOrganizationId());
            }
        }

        return true;
    }

    /**
     * Clears the tenant context after the request completes to prevent
     * ThreadLocal leakage across pooled servlet threads.
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        TenantContext.clear();
    }
}
