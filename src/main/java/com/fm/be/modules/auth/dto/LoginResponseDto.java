package com.fm.be.modules.auth.dto;

import com.fm.be.common.constants.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO returned after successful authentication.
 * Contains the user's basic info and JWT token pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    /** The user's UUID */
    private UUID userId;

    /** User's display name or email */
    private String email;

    /** User's phone number (if available) */
    private String phone;

    /** The user's role */
    private Role role;

    /** The user's organization ID */
    private UUID organizationId;

    /** Short-lived access token (15 min) */
    private String accessToken;

    /** Long-lived refresh token (7d web / 30d mobile) */
    private String refreshToken;
}
