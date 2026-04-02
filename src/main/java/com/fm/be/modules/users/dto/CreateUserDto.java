package com.fm.be.modules.users.dto;

import com.fm.be.common.constants.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for creating a new user.
 * Admin endpoints only — at least one of phone or email must be provided.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserDto {

    /** Phone number in E.164 format (optional if email provided) */
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phone;

    /** Email address (optional if phone provided) */
    @Email(message = "Invalid email format")
    private String email;

    /** Password (required for email/password login users) */
    private String password;

    /** User role */
    @NotNull(message = "Role is required")
    private Role role;

    /** Preferred language (default: en) */
    @Builder.Default
    private String preferredLang = "en";

    /** Organization ID this user belongs to */
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;
}
