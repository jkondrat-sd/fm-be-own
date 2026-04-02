package com.fm.be.common.security;

import com.fm.be.common.constants.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtTokenProvider}.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-padding-here";
    private static final long ACCESS_EXPIRATION = 900000;   // 15 min
    private static final long REFRESH_EXPIRATION = 604800000; // 7 days

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    void generateAccessToken_createsValidToken() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        String token = tokenProvider.generateAccessToken(
                userId, Role.ADMIN, orgId, List.of(siteId));

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void extractUserPrincipal_returnsCorrectClaims() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID siteId1 = UUID.randomUUID();
        UUID siteId2 = UUID.randomUUID();

        String token = tokenProvider.generateAccessToken(
                userId, Role.EMPLOYEE, orgId, List.of(siteId1, siteId2));

        UserPrincipal principal = tokenProvider.extractUserPrincipal(token);

        assertEquals(userId, principal.getUserId());
        assertEquals(Role.EMPLOYEE, principal.getRole());
        assertEquals(orgId, principal.getOrganizationId());
        assertEquals(2, principal.getSiteIds().size());
        assertTrue(principal.getSiteIds().contains(siteId1));
        assertTrue(principal.getSiteIds().contains(siteId2));
    }

    @Test
    void extractUserPrincipal_withEmptySiteIds() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = tokenProvider.generateAccessToken(
                userId, Role.SUPER_ADMIN, orgId, List.of());

        UserPrincipal principal = tokenProvider.extractUserPrincipal(token);

        assertEquals(userId, principal.getUserId());
        assertTrue(principal.getSiteIds().isEmpty());
    }

    @Test
    void extractUserId_returnsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = tokenProvider.generateAccessToken(
                userId, Role.ADMIN, orgId, List.of());

        assertEquals(userId, tokenProvider.extractUserId(token));
    }

    @Test
    void generateRefreshToken_createsValidToken() {
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateRefreshToken(userId);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(userId, tokenProvider.extractUserId(token));
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        // Create provider with 0ms expiration (instant expiry)
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0, 0);
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = expiredProvider.generateAccessToken(
                userId, Role.EMPLOYEE, orgId, List.of());

        // Token should be expired immediately
        assertFalse(expiredProvider.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForMalformedToken() {
        assertFalse(tokenProvider.validateToken("not.a.valid.jwt"));
    }

    @Test
    void validateToken_returnsFalseForWrongSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "other-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-padding",
                ACCESS_EXPIRATION, REFRESH_EXPIRATION);

        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String token = otherProvider.generateAccessToken(
                userId, Role.ADMIN, orgId, List.of());

        // Token signed with different secret should fail validation
        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForEmptyToken() {
        assertFalse(tokenProvider.validateToken(""));
    }

    @Test
    void allRoles_canBeUsedInTokens() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        for (Role role : Role.values()) {
            String token = tokenProvider.generateAccessToken(
                    userId, role, orgId, List.of());

            UserPrincipal principal = tokenProvider.extractUserPrincipal(token);
            assertEquals(role, principal.getRole());
        }
    }
}
