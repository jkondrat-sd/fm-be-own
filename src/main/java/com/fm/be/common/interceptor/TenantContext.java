package com.fm.be.common.interceptor;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant's organization ID.
 *
 * <p>Set by {@link TenantInterceptor} on each request from the JWT claims.
 * All repository queries should filter by this organization ID to enforce
 * multi-tenant data isolation.</p>
 *
 * <p>IMPORTANT: Always call {@link #clear()} in an afterCompletion handler
 * to prevent ThreadLocal leakage across pooled threads.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORG =
            new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Sets the current tenant organization ID.
     *
     * @param organizationId the org UUID from JWT
     */
    public static void setOrganizationId(UUID organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    /**
     * Gets the current tenant organization ID.
     *
     * @return the org UUID, or null if not set
     */
    public static UUID getOrganizationId() {
        return CURRENT_ORG.get();
    }

    /**
     * Clears the tenant context. Must be called after request completes.
     */
    public static void clear() {
        CURRENT_ORG.remove();
    }
}
