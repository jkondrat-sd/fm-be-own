package com.fm.be.modules.auth;

import com.fm.be.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing refresh tokens.
 * Provides lookup by token string, deletion by user, and expired token cleanup.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a refresh token by its token string.
     *
     * @param token the refresh token string
     * @return the RefreshToken entity if found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Deletes all refresh tokens for a specific user (used on full logout).
     *
     * @param userId the user's UUID
     */
    @Modifying
    void deleteByUserId(UUID userId);

    /**
     * Deletes a specific refresh token by its token string (single-device logout).
     *
     * @param token the refresh token string
     */
    @Modifying
    void deleteByToken(String token);

    /**
     * Removes all expired refresh tokens (housekeeping).
     *
     * @param now the current timestamp
     * @return number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
}
