package com.fm.be.common.security;

import com.fm.be.common.constants.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-padding-here";

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider tokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenProvider = new JwtTokenProvider(SECRET, 900000, 604800000);
        filter = new JwtAuthenticationFilter(tokenProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_setsSecurityContext() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, Role.ADMIN, orgId, List.of());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        // Security context should be set
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertEquals(userId, principal.getUserId());
        assertEquals(Role.ADMIN, principal.getRole());

        // Filter chain should continue
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingToken_passesThroughWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        // No authentication set
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Filter chain should continue
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_passesThroughWithoutAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token.here");

        filter.doFilterInternal(request, response, filterChain);

        // No authentication set
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Filter chain should continue
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noBearerPrefix_passesThroughWithoutAuth() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, Role.ADMIN, orgId, List.of());

        // Missing "Bearer " prefix
        when(request.getHeader("Authorization")).thenReturn(token);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void expiredToken_passesThroughWithoutAuth() throws Exception {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0, 0);
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = expiredProvider.generateAccessToken(userId, Role.EMPLOYEE, orgId, List.of());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
