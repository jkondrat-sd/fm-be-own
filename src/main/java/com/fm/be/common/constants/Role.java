package com.fm.be.common.constants;

/**
 * All user roles in the FM platform.
 * Ordered from least to most privilege.
 *
 * <ul>
 *   <li>{@code EMPLOYEE} — Field employees (mobile app)</li>
 *   <li>{@code SUPERVISOR} — Site supervisors (mobile + limited web)</li>
 *   <li>{@code ADMIN} — Admin users (web portal)</li>
 *   <li>{@code SUPER_ADMIN} — System administrators (full access)</li>
 *   <li>{@code CLIENT} — External clients (read-only client portal)</li>
 * </ul>
 */
public enum Role {
    EMPLOYEE,
    SUPERVISOR,
    ADMIN,
    SUPER_ADMIN,
    CLIENT
}
