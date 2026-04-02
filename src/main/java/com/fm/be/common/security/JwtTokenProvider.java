package com.fm.be.common.security;

import com.fm.be.common.constants.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT token provider for generating, validating, and extracting claims from tokens.
 *
 * <p>Access tokens are short-lived (15min default). Refresh tokens are long-lived
 * (7d web, 30d mobile). The secret key must be at least 256 bits for HS256.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Generates a short-lived access token.
     *
     * @param userId         the user's UUID
     * @param role           the user's role
     * @param organizationId the user's organization UUID
     * @param siteIds        list of site UUIDs the user has access to
     * @return signed JWT access token
     */
    public String generateAccessToken(UUID userId, Role role, UUID organizationId, List<UUID> siteIds) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + accessTokenExpirationMs);

        List<String> siteIdStrings = siteIds != null
                ? siteIds.stream().map(UUID::toString).collect(Collectors.toList())
                : List.of();

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .claim("organizationId", organizationId.toString())
                .claim("siteIds", siteIdStrings)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generates a long-lived refresh token.
     *
     * @param userId the user's UUID
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validates a token and returns true if valid (not expired, correctly signed).
     *
     * @param token the JWT to validate
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("Invalid JWT: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT token is null or empty: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Extracts the UserPrincipal from a valid access token.
     *
     * @param token the JWT access token
     * @return UserPrincipal with userId, role, orgId, siteIds
     */
    public UserPrincipal extractUserPrincipal(String token) {
        Claims claims = extractAllClaims(token);

        UUID userId = UUID.fromString(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        UUID organizationId = UUID.fromString(claims.get("organizationId", String.class));

        @SuppressWarnings("unchecked")
        List<String> siteIdStrings = claims.get("siteIds", List.class);
        List<UUID> siteIds = siteIdStrings != null
                ? siteIdStrings.stream().map(UUID::fromString).collect(Collectors.toList())
                : List.of();

        return UserPrincipal.builder()
                .userId(userId)
                .role(role)
                .organizationId(organizationId)
                .siteIds(siteIds)
                .build();
    }

    /**
     * Extracts the user ID from a token (works for both access and refresh tokens).
     *
     * @param token the JWT
     * @return the userId UUID from the subject claim
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    /**
     * Parses all claims from a JWT.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
