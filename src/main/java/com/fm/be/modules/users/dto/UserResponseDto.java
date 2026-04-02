package com.fm.be.modules.users.dto;

import com.fm.be.common.constants.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for user data returned from API endpoints.
 * Excludes sensitive fields like passwordHash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    private UUID id;
    private String phone;
    private String email;
    private Role role;
    private boolean isActive;
    private String preferredLang;
    private UUID organizationId;
    private Instant createdAt;
    private Instant updatedAt;
}
